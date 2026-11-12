package com.example.backend.auth;

import com.example.backend.support.PostgresIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.backend.auth.token.InvalidTokenException;
import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.mail.VerificationEmailRequested;
import com.example.backend.mail.VerificationMailPublisher;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"})
class ResendVerificationApiTests extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    @Autowired Environment environment;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserTokenRepository tokens;
    @Autowired TokenService tokenService;
    @Autowired ResendVerificationService resend;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean Clock clock;
    @MockitoBean VerificationMailPublisher publisher;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void setClock() { when(clock.instant()).thenReturn(NOW); }

    @Test
    void replacesUnusedVerificationTokensButKeepsOtherUsersAndPasswordResetTokens() throws Exception {
        User owner = user();
        var first = issueAt(owner, NOW.minusSeconds(90), TokenType.EMAIL_VERIFICATION);
        var second = issueAt(owner, NOW.minusSeconds(61), TokenType.EMAIL_VERIFICATION);
        var reset = issueAt(owner, NOW, TokenType.PASSWORD_RESET);
        var other = issueAt(user(), NOW, TokenType.EMAIL_VERIFICATION);
        var response = post("/resend-verification", Map.of("email", "  " + owner.getEmail().toUpperCase() + "  "));
        assertAccepted(response);
        assertTrue(tokens.findByTokenHash(hash(first.value())).isEmpty());
        assertTrue(tokens.findByTokenHash(hash(second.value())).isEmpty());
        assertTrue(tokens.findByTokenHash(hash(reset.value())).isPresent());
        assertTrue(tokens.findByTokenHash(hash(other.value())).isPresent());
        var message = sentMessage();
        assertEquals(owner.getId(), message.userId());
        assertEquals(owner.getEmail(), message.recipient());
        assertFalse(response.body().contains(owner.getEmail()));
        assertFalse(response.body().contains(message.token()));
        assertThrows(InvalidTokenException.class, () -> tokenService.consume(first.value(), TokenType.EMAIL_VERIFICATION));
        assertEquals(200, post("/verify-email", Map.of("token", message.token())).statusCode());
    }

    @Test
    void enforcesFullSixtySecondsWithoutExtendingCooldownForRejectedAttempts() throws Exception {
        User owner = user();
        var original = tokenService.issue(owner.getId(), TokenType.EMAIL_VERIFICATION);
        when(clock.instant()).thenReturn(NOW.plusMillis(59999));
        assertAccepted(post("/resend-verification", Map.of("email", owner.getEmail())));
        verifyNoInteractions(publisher);
        assertTrue(tokens.findByTokenHash(hash(original.value())).isPresent());

        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        assertAccepted(post("/resend-verification", Map.of("email", owner.getEmail())));
        assertTrue(tokens.findByTokenHash(hash(original.value())).isEmpty());
        String replacement = sentMessage().token();
        assertAccepted(post("/resend-verification", Map.of("email", owner.getEmail())));
        verify(publisher, times(1)).afterRegistrationCommitted(any());
        assertTrue(tokens.findByTokenHash(hash(replacement)).isPresent());
    }

    @Test
    void registrationStartsCooldownAndImmediateResendDoesNotReplaceItsToken() throws Exception {
        String name = uniqueName();
        assertEquals(201, post("/register", Map.of("username", name, "email", name + "@example.invalid",
                "password", "ExamplePassword123!")).statusCode());
        String original = sentMessage().token();
        assertAccepted(post("/resend-verification", Map.of("email", name + "@example.invalid")));
        verify(publisher, times(1)).afterRegistrationCommitted(any());
        assertTrue(tokens.findByTokenHash(hash(original)).isPresent());
    }

    @Test
    void hidesUnknownConfirmedBlockedAndCooldownStatesWithTheSameResponse() throws Exception {
        User confirmed = user();
        confirmed.setEmailVerifiedAt(NOW);
        users.saveAndFlush(confirmed);
        User blocked = user();
        blocked.setEnabled(false);
        users.saveAndFlush(blocked);
        User limited = user();
        tokenService.issue(limited.getId(), TokenType.EMAIL_VERIFICATION);
        long count = tokens.count();
        var unknown = post("/resend-verification", Map.of("email", "unknown-" + UUID.randomUUID() + "@example.invalid"));
        assertAccepted(unknown);
        for (User owner : new User[]{confirmed, blocked, limited}) {
            var response = post("/resend-verification", Map.of("email", owner.getEmail()));
            assertAccepted(response);
            assertEquals(unknown.body(), response.body());
        }
        assertEquals(count, tokens.count());
        verifyNoInteractions(publisher);
    }

    @Test
    void canRecoverAnAccountWithNoVerificationToken() throws Exception {
        User owner = user();
        assertAccepted(post("/resend-verification", Map.of("email", owner.getEmail())));
        var event = sentMessage();
        assertEquals(owner.getId(), event.userId());
        assertTrue(tokens.findByTokenHash(hash(event.token())).isPresent());
    }

    @Test
    void invalidEmailAndGetReturnErrorsWithoutIssuingTokens() throws Exception {
        long count = tokens.count();
        for (Map<String, ?> request : java.util.List.of(Map.<String, Object>of(), Map.of("email", "bad-address"), Map.of("email", " "))) {
            var response = post("/resend-verification", request);
            assertEquals(400, response.statusCode());
            assertTrue(json.readTree(response.body()).get("fieldErrors").has("email"));
        }
        var get = HttpRequest.newBuilder(endpoint("/resend-verification")).GET().build();
        assertEquals(405, http.send(get, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(count, tokens.count());
        verifyNoInteractions(publisher);
    }

    @Test
    void rollbackRestoresPreviousLinksAndDoesNotPublish() throws Exception {
        User owner = user();
        var original = issueAt(owner, NOW.minusSeconds(61), TokenType.EMAIL_VERIFICATION);
        long count = tokens.count();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            resend.resend(owner.getEmail());
            status.setRollbackOnly();
        });
        assertEquals(count, tokens.count());
        assertTrue(tokens.findByTokenHash(hash(original.value())).isPresent());
        verifyNoInteractions(publisher);
        assertEquals(owner.getId(), tokenService.consume(original.value(), TokenType.EMAIL_VERIFICATION).getId());
    }

    @Test
    void concurrentResendsCreateOnlyOneReplacementAndOneEmail() throws Exception {
        User owner = user();
        var original = issueAt(owner, NOW.minusSeconds(61), TokenType.EMAIL_VERIFICATION);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> call = () -> {
                start.await();
                return post("/resend-verification", Map.of("email", owner.getEmail())).statusCode();
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            start.countDown();
            assertEquals(202, first.get(10, TimeUnit.SECONDS));
            assertEquals(202, second.get(10, TimeUnit.SECONDS));
        }
        assertTrue(tokens.findByTokenHash(hash(original.value())).isEmpty());
        assertTrue(tokens.findByTokenHash(hash(sentMessage().token())).isPresent());
    }

    @Test
    void concurrentVerificationAndResendAreSerializedWithoutDeadlockOrStaleConfirmation() throws Exception {
        User owner = user();
        var original = issueAt(owner, NOW.minusSeconds(61), TokenType.EMAIL_VERIFICATION);
        var start = new CountDownLatch(1);
        int verificationStatus;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var verify = executor.submit(() -> {
                start.await();
                return post("/verify-email", Map.of("token", original.value())).statusCode();
            });
            var resendCall = executor.submit(() -> {
                start.await();
                return post("/resend-verification", Map.of("email", owner.getEmail())).statusCode();
            });
            start.countDown();
            verificationStatus = verify.get(10, TimeUnit.SECONDS);
            assertEquals(202, resendCall.get(10, TimeUnit.SECONDS));
        }
        if (verificationStatus == 200) {
            assertNotNull(users.findById(owner.getId()).orElseThrow().getEmailVerifiedAt());
            verifyNoInteractions(publisher);
        } else {
            assertEquals(400, verificationStatus);
            assertNull(users.findById(owner.getId()).orElseThrow().getEmailVerifiedAt());
            assertTrue(tokens.findByTokenHash(hash(original.value())).isEmpty());
            assertNotNull(sentMessage());
        }
    }

    private TokenService.IssuedToken issueAt(User user, Instant time, TokenType type) {
        when(clock.instant()).thenReturn(time);
        var issued = tokenService.issue(user.getId(), type);
        when(clock.instant()).thenReturn(NOW);
        return issued;
    }

    private VerificationEmailRequested sentMessage() {
        var captured = ArgumentCaptor.forClass(VerificationEmailRequested.class);
        verify(publisher, times(1)).afterRegistrationCommitted(captured.capture());
        return captured.getValue();
    }

    private void assertAccepted(HttpResponse<String> response) throws Exception {
        assertEquals(202, response.statusCode());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        var body = json.readTree(response.body());
        assertEquals(60, body.get("retryAfterSeconds").asInt());
        assertTrue(body.get("message").asText().startsWith("If this address needs verification"));
    }

    private String uniqueName() { return "resend_" + UUID.randomUUID().toString().replace("-", ""); }
    private User user() {
        String name = uniqueName();
        return users.saveAndFlush(new User(name, name + "@example.invalid", "test-hash"));
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private URI endpoint(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + "/api/auth" + path);
    }

    private HttpResponse<String> post(String path, Map<String, ?> body) throws Exception {
        return com.example.backend.support.CsrfHttpClient.post(endpoint(path), json.writeValueAsString(body), json);
    }
}
