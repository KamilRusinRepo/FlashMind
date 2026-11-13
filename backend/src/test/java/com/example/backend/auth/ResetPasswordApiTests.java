package com.example.backend.auth;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.support.CsrfHttpClient;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResetPasswordApiTests extends PostgresIntegrationTest {
    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "  NewPassword123!  ";
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired TokenService tokens;
    @Autowired UserTokenRepository storedTokens;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test
    void resetsPasswordOnceAndRevokesOwnerSessionsWithoutAffectingOtherAccounts() throws Exception {
        var owner = account();
        var other = account();
        var token = tokens.issue(owner.getId(), TokenType.PASSWORD_RESET);
        tokens.issue(owner.getId(), TokenType.EMAIL_VERIFICATION);
        var anotherReset = tokens.issue(owner.getId(), TokenType.PASSWORD_RESET);
        try (var first = new Client(); var second = new Client(); var unrelated = new Client()) {
            assertEquals(200, first.login(owner, OLD_PASSWORD));
            assertEquals(200, second.login(owner, OLD_PASSWORD));
            assertEquals(200, unrelated.login(other, OLD_PASSWORD));
            var response = reset(token.value(), NEW_PASSWORD);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("{\"passwordReset\":true}", response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var updated = users.findById(owner.getId()).orElseThrow();
            assertTrue(encoder.matches(NEW_PASSWORD, updated.getPasswordHash()));
            assertFalse(encoder.matches(OLD_PASSWORD, updated.getPasswordHash()));
            assertEquals(owner.getSessionVersion() + 1, updated.getSessionVersion());
            assertEquals(1, storedTokens.findAll().stream().filter(value -> value.getUser().getId().equals(owner.getId())
                    && value.getType() == TokenType.PASSWORD_RESET && value.getUsedAt() != null).count());
            assertEquals(400, reset(token.value(), "AnotherPassword123!").statusCode());
            assertEquals(400, reset(anotherReset.value(), "AnotherPassword123!").statusCode());
            assertEquals(401, first.getMe());
            assertEquals(401, second.getMe());
            assertEquals(200, unrelated.getMe());
            assertEquals(401, first.login(owner, OLD_PASSWORD));
            assertEquals(200, first.login(owner, NEW_PASSWORD));
            assertEquals(200, first.getMe());
            assertTrue(users.findById(other.getId()).isPresent());
            assertEquals(1, storedTokens.findAll().stream().filter(value -> value.getUser().getId().equals(owner.getId())
                    && value.getType() == TokenType.EMAIL_VERIFICATION && value.getUsedAt() == null).count());
        }
    }

    @Test
    void rejectsWrongPurposeMalformedTokensAndInvalidPasswordWithoutChangingAccount() throws Exception {
        var owner = account();
        var verification = tokens.issue(owner.getId(), TokenType.EMAIL_VERIFICATION);
        var resetToken = tokens.issue(owner.getId(), TokenType.PASSWORD_RESET);
        assertEquals(400, reset(verification.value(), NEW_PASSWORD).statusCode());
        assertEquals(400, reset("invalid", NEW_PASSWORD).statusCode());
        assertEquals(400, reset("x".repeat(43), NEW_PASSWORD).statusCode());
        var response = reset(resetToken.value(), "ą".repeat(37));
        assertEquals(400, response.statusCode());
        assertTrue(json.readTree(response.body()).get("fieldErrors").has("password"));
        assertTrue(encoder.matches(OLD_PASSWORD, users.findById(owner.getId()).orElseThrow().getPasswordHash()));
        assertTrue(storedTokens.findAll().stream().noneMatch(value -> value.getUsedAt() != null));
    }

    @Test
    void blockedAccountRollsBackTokenConsumptionAndPasswordChanges() throws Exception {
        var owner = account();
        var token = tokens.issue(owner.getId(), TokenType.PASSWORD_RESET);
        owner.setEnabled(false);
        users.saveAndFlush(owner);
        assertEquals(400, reset(token.value(), NEW_PASSWORD).statusCode());
        var unchanged = users.findById(owner.getId()).orElseThrow();
        assertFalse(unchanged.isEnabled());
        assertEquals(owner.getSessionVersion(), unchanged.getSessionVersion());
        assertTrue(encoder.matches(OLD_PASSWORD, unchanged.getPasswordHash()));
        assertTrue(storedTokens.findAll().stream().noneMatch(value -> value.getUsedAt() != null));
    }

    private User account() {
        String name = "Reset" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(OLD_PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        return users.saveAndFlush(user);
    }

    private URI endpoint(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }

    private HttpResponse<String> reset(String token, String password) throws Exception {
        return CsrfHttpClient.post(endpoint("/api/auth/reset-password"), json.writeValueAsString(Map.of("token", token, "password", password)), json);
    }

    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        int getMe() throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint("/api/auth/me")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        }
        int login(User user, String password) throws Exception {
            var csrf = http.send(HttpRequest.newBuilder(endpoint("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, csrf.statusCode());
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", json.readTree(csrf.body()).get("token").asText())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", password)))).build();
            return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
        }
        @Override public void close() { http.close(); }
    }
}
