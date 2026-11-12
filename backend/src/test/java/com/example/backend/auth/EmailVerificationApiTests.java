package com.example.backend.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserToken;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"})
class EmailVerificationApiTests {
    @Autowired Environment environment;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserTokenRepository tokens;
    @Autowired TokenService tokenService;
    @Autowired EmailVerificationService verification;
    @Autowired PlatformTransactionManager transactionManager;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void confirmsOnlyTokenOwnerAndConsumesTokenWithoutExposingAccountDetails() throws Exception {
        User owner = user();
        User other = user();
        var issued = tokenService.issue(owner.getId(), TokenType.EMAIL_VERIFICATION);
        var response = post(Map.of("token", issued.value()));

        assertEquals(200, response.statusCode());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("{\"emailVerified\":true}", response.body());
        assertNotNull(users.findById(owner.getId()).orElseThrow().getEmailVerifiedAt());
        assertNull(users.findById(other.getId()).orElseThrow().getEmailVerifiedAt());
        assertNotNull(stored(issued.value()).getUsedAt());
        assertFalse(response.body().contains(issued.value()));
    }

    @Test
    void reusedTokenReturns400WithoutChangingConfirmationDate() throws Exception {
        User owner = user();
        var issued = tokenService.issue(owner.getId(), TokenType.EMAIL_VERIFICATION);
        assertEquals(200, post(Map.of("token", issued.value())).statusCode());
        Instant confirmedAt = users.findById(owner.getId()).orElseThrow().getEmailVerifiedAt();
        var response = post(Map.of("token", issued.value()));
        assertInvalid(response, issued.value());
        assertEquals(confirmedAt, users.findById(owner.getId()).orElseThrow().getEmailVerifiedAt());
    }

    @Test
    void expiredTokenDoesNotConfirmAccountOrBecomeUsed() throws Exception {
        User owner = user();
        String value = "E".repeat(43);
        Instant now = Instant.now();
        tokens.saveAndFlush(new UserToken(owner, hash(value), TokenType.EMAIL_VERIFICATION,
                now.minusSeconds(120), now.minusSeconds(60)));
        assertInvalid(post(Map.of("token", value)), value);
        assertNull(users.findById(owner.getId()).orElseThrow().getEmailVerifiedAt());
        assertNull(stored(value).getUsedAt());
    }

    @Test
    void passwordResetTokenCannotConfirmEmailAndRemainsUnused() throws Exception {
        User owner = user();
        var issued = tokenService.issue(owner.getId(), TokenType.PASSWORD_RESET);
        assertInvalid(post(Map.of("token", issued.value())), issued.value());
        assertNull(users.findById(owner.getId()).orElseThrow().getEmailVerifiedAt());
        assertNull(stored(issued.value()).getUsedAt());
    }

    @Test
    void missingMalformedAndUnknownTokensReturnSafeErrors() throws Exception {
        assertEquals(400, post(Map.of()).statusCode());
        assertInvalid(post(Map.of("token", "secret-invalid-value")), "secret-invalid-value");
        assertInvalid(post(Map.of("token", "U".repeat(43))), "U".repeat(43));
    }

    @Test
    void getRequestDoesNotPerformConfirmation() throws Exception {
        User owner = user();
        var issued = tokenService.issue(owner.getId(), TokenType.EMAIL_VERIFICATION);
        var request = HttpRequest.newBuilder(endpoint()).GET().build();
        assertEquals(405, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertNull(users.findById(owner.getId()).orElseThrow().getEmailVerifiedAt());
        assertNull(stored(issued.value()).getUsedAt());
    }

    @Test
    void confirmationDoesNotEnableBlockedAccountOrReplaceExistingConfirmationDate() throws Exception {
        User owner = user();
        owner.setEnabled(false);
        Instant originalDate = Instant.parse("2026-01-01T00:00:00Z");
        owner.setEmailVerifiedAt(originalDate);
        users.saveAndFlush(owner);
        var issued = tokenService.issue(owner.getId(), TokenType.EMAIL_VERIFICATION);
        assertEquals(200, post(Map.of("token", issued.value())).statusCode());
        User storedUser = users.findById(owner.getId()).orElseThrow();
        assertFalse(storedUser.isEnabled());
        assertEquals(originalDate, storedUser.getEmailVerifiedAt());
    }

    @Test
    void outerRollbackRestoresBothAccountAndToken() throws Exception {
        User owner = user();
        var issued = tokenService.issue(owner.getId(), TokenType.EMAIL_VERIFICATION);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertTrue(verification.verify(issued.value()).emailVerified());
            status.setRollbackOnly();
        });
        assertNull(users.findById(owner.getId()).orElseThrow().getEmailVerifiedAt());
        assertNull(stored(issued.value()).getUsedAt());
        assertEquals(200, post(Map.of("token", issued.value())).statusCode());
    }

    @Test
    void concurrentHttpRequestsConsumeSingleUseTokenOnlyOnce() throws Exception {
        var issued = tokenService.issue(user().getId(), TokenType.EMAIL_VERIFICATION);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> call = () -> {
                start.await();
                return post(Map.of("token", issued.value())).statusCode();
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            start.countDown();
            int a = first.get(10, TimeUnit.SECONDS);
            int b = second.get(10, TimeUnit.SECONDS);
            assertTrue((a == 200 && b == 400) || (a == 400 && b == 200));
        }
    }

    private User user() {
        String name = "verify_" + UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(new User(name, name + "@example.invalid", "test-hash"));
    }

    private UserToken stored(String token) throws Exception {
        return tokens.findByTokenHash(hash(token)).orElseThrow();
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertInvalid(HttpResponse<String> response, String token) throws Exception {
        assertEquals(400, response.statusCode());
        var body = json.readTree(response.body());
        assertEquals(400, body.get("status").asInt());
        assertTrue(body.get("fieldErrors").has("token"));
        assertFalse(response.body().contains(token));
    }

    private URI endpoint() {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + "/api/auth/verify-email");
    }

    private HttpResponse<String> post(Map<String, ?> body) throws Exception {
        return com.example.backend.support.CsrfHttpClient.post(endpoint(), json.writeValueAsString(body), json);
    }
}
