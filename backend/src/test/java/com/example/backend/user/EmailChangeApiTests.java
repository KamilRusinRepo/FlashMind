package com.example.backend.user;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.example.backend.auth.token.InvalidTokenException;
import com.example.backend.auth.ResetPasswordService;
import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.mail.EmailChangeMailPublisher;
import com.example.backend.mail.EmailChangeRequested;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.dto.request.RequestEmailChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmailChangeApiTests extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-14T16:00:00Z");
    private static final String PASSWORD = "EmailChange123!";
    private static final String REQUEST = "/api/users/me/email-change";
    private static final String CONFIRM = "/api/auth/confirm-email-change";
    @Autowired UserRepository users;
    @Autowired UserTokenRepository storedTokens;
    @Autowired TokenService tokens;
    @Autowired EmailChangeService changes;
    @Autowired ResetPasswordService passwords;
    @Autowired PasswordEncoder encoder;
    @Autowired Environment environment;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    @MockitoBean EmailChangeMailPublisher publisher;

    @BeforeEach void setClock() { when(clock.instant()).thenReturn(NOW); }

    @Test
    void keepsOldAddressUntilConfirmationThenUpdatesEmailAndInvalidatesSessionsAndOldResetLinks() throws Exception {
        var owner = account();
        var reset = tokens.issue(owner.getId(), TokenType.PASSWORD_RESET);
        try (var client = new Client(); var otherSession = new Client(); var anonymous = new Client()) {
            client.login(owner.getEmail()); otherSession.login(owner.getUsername());
            var response = client.post(REQUEST, Map.of("email", "  NEW@example.invalid  ", "currentPassword", PASSWORD), true);
            assertEquals(202, response.statusCode(), response.body());
            var body = json.readTree(response.body());
            assertEquals("new@example.invalid", body.get("pendingEmail").asText());
            assertEquals(60, body.get("retryAfterSeconds").asInt());
            assertEquals(owner.getEmail(), users.findById(owner.getId()).orElseThrow().getEmail());
            assertEquals(owner.getEmail(), json.readTree(client.get("/api/auth/me").body()).get("email").asText());
            assertEquals("new@example.invalid", json.readTree(client.get(REQUEST).body()).get("pendingEmail").asText());
            var event = event();
            assertEquals(owner.getId(), event.userId());
            assertEquals("new@example.invalid", event.recipient());
            assertThrows(InvalidTokenException.class, () -> tokens.consume(event.token(), TokenType.EMAIL_VERIFICATION));
            assertFalse(response.body().contains(event.token()));
            assertFalse(response.body().contains(PASSWORD));
            assertEquals(401, anonymous.loginResponse("new@example.invalid").statusCode());
            assertEquals(200, anonymous.loginResponse(owner.getEmail()).statusCode());
            // The public confirmation is bound to the token owner, not the logged-in account.
            assertEquals(200, anonymous.post(CONFIRM, Map.of("token", event.token()), true).statusCode());
            var saved = users.findById(owner.getId()).orElseThrow();
            assertEquals("new@example.invalid", saved.getEmail());
            assertNotNull(saved.getEmailVerifiedAt());
            assertEquals(owner.getUsername(), saved.getUsername());
            assertEquals(owner.getPasswordHash(), saved.getPasswordHash());
            assertEquals(owner.getSessionVersion() + 1, saved.getSessionVersion());
            assertEquals(401, client.get("/api/auth/me").statusCode());
            assertEquals(401, otherSession.get("/api/auth/me").statusCode());
            assertThrows(InvalidTokenException.class, () -> tokens.consume(reset.value(), TokenType.PASSWORD_RESET));
            assertEquals(400, anonymous.post(CONFIRM, Map.of("token", event.token()), true).statusCode());
            assertEquals(401, anonymous.loginResponse(owner.getEmail()).statusCode());
            assertEquals(200, anonymous.loginResponse("new@example.invalid").statusCode());
        }
    }

    @Test
    void resendReplacesLinkAndExpiredOrWrongPurposeTokensCannotChangeEmail() throws Exception {
        var owner = account();
        try (var client = new Client()) {
            client.login(owner.getUsername());
            assertEquals(202, client.post(REQUEST, Map.of("email", "first@example.invalid", "currentPassword", PASSWORD), true).statusCode());
            String first = event().token();
            assertEquals(429, client.post(REQUEST, Map.of("email", "second@example.invalid", "currentPassword", PASSWORD), true).statusCode());
            when(clock.instant()).thenReturn(NOW.plusSeconds(61));
            assertEquals(202, client.post(REQUEST, Map.of("email", "second@example.invalid", "currentPassword", PASSWORD), true).statusCode());
            var events = ArgumentCaptor.forClass(EmailChangeRequested.class);
            verify(publisher, times(2)).publish(events.capture());
            String second = events.getAllValues().getLast().token();
            assertEquals(400, client.post(CONFIRM, Map.of("token", first), true).statusCode());
            var wrongPurpose = tokens.issue(owner.getId(), TokenType.PASSWORD_RESET);
            assertEquals(400, client.post(CONFIRM, Map.of("token", wrongPurpose.value()), true).statusCode());
            when(clock.instant()).thenReturn(NOW.plusSeconds(90000));
            assertEquals(400, client.post(CONFIRM, Map.of("token", second), true).statusCode());
            assertTrue(json.readTree(client.get(REQUEST).body()).get("pendingEmail").isNull());
            assertEquals(owner.getEmail(), users.findById(owner.getId()).orElseThrow().getEmail());
        }
    }

    @Test
    void protectsRequestsAndRechecksAvailabilityAtConfirmationWithoutConsumingFailedToken() throws Exception {
        var owner = account();
        var other = account();
        try (var client = new Client()) {
            assertEquals(401, client.get(REQUEST).statusCode());
            assertEquals(401, client.post(REQUEST, Map.of("email", "new@example.invalid", "currentPassword", PASSWORD), true).statusCode());
            client.login(owner.getUsername());
            assertEquals(403, client.post(REQUEST, Map.of("email", "new@example.invalid", "currentPassword", PASSWORD), false).statusCode());
            assertEquals(400, client.post(REQUEST, Map.of("email", "new@example.invalid", "currentPassword", "incorrect"), true).statusCode());
            assertEquals(400, client.post(REQUEST, Map.of("email", "invalid", "currentPassword", PASSWORD), true).statusCode());
            assertEquals(400, client.post(REQUEST, Map.of("email", owner.getEmail(), "currentPassword", PASSWORD), true).statusCode());
            assertEquals(409, client.post(REQUEST, Map.of("email", other.getEmail().toUpperCase(java.util.Locale.ROOT), "currentPassword", PASSWORD), true).statusCode());
            verifyNoInteractions(publisher);
            var issued = tokens.issueEmailChange(owner.getId(), "claimed@example.invalid");
            other.setEmail("claimed@example.invalid"); users.saveAndFlush(other);
            assertEquals(403, client.post(CONFIRM, Map.of("token", issued.value()), false).statusCode());
            assertEquals(409, client.post(CONFIRM, Map.of("token", issued.value()), true).statusCode());
            assertEquals(owner.getEmail(), users.findById(owner.getId()).orElseThrow().getEmail());
            var pending = storedTokens.findFirstByUser_IdAndTypeOrderByCreatedAtDesc(owner.getId(), TokenType.EMAIL_CHANGE).orElseThrow();
            assertNull(pending.getUsedAt());
            var reset = tokens.issue(owner.getId(), TokenType.PASSWORD_RESET);
            passwords.reset(reset.value(), "NewPassword123!");
            assertThrows(InvalidTokenException.class, () -> tokens.consumeEmailChange(issued.value()));
        }
    }

    @Test
    void rollbackDoesNotPublishAnEmailOrLeavePendingChange() {
        var owner = account();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            changes.request(owner.getId(), new RequestEmailChange("rolledback@example.invalid", PASSWORD));
            status.setRollbackOnly();
        });
        verifyNoInteractions(publisher);
        assertTrue(storedTokens.findFirstByUser_IdAndTypeOrderByCreatedAtDesc(owner.getId(), TokenType.EMAIL_CHANGE).isEmpty());
        assertEquals(owner.getEmail(), users.findById(owner.getId()).orElseThrow().getEmail());
    }

    private EmailChangeRequested event() {
        var captured = ArgumentCaptor.forClass(EmailChangeRequested.class);
        verify(publisher).publish(captured.capture());
        return captured.getValue();
    }
    private User account() {
        String name = "Email" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(NOW.minusSeconds(3600));
        return users.saveAndFlush(user);
    }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> get(String path) throws Exception { return http.send(HttpRequest.newBuilder(endpoint(path)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        HttpResponse<String> post(String path, Map<String, ?> body, boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(endpoint(path)).header("Content-Type", "application/json");
            if (csrf) request.header("X-CSRF-TOKEN", json.readTree(get("/api/auth/csrf").body()).get("token").asText());
            return http.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> loginResponse(String login) throws Exception { return post("/api/auth/login", Map.of("login", login, "password", PASSWORD), true); }
        void login(String login) throws Exception { var response = loginResponse(login); assertEquals(200, response.statusCode(), response.body()); }
        @Override public void close() { http.close(); }
    }
}
