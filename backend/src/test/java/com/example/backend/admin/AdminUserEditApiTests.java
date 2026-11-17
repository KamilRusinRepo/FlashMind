package com.example.backend.admin;

import com.example.backend.auth.token.*;
import com.example.backend.mail.EmailChangeMailPublisher;
import com.example.backend.mail.EmailChangeRequested;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserEditApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "AdminEditTests123!";
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired TokenService tokens;
    @Autowired UserTokenRepository storedTokens;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;
    @MockitoBean EmailChangeMailPublisher publisher;

    @Test void editsUsernameAndRolesRevokesSessionsAndOldTokensAndLeavesPasswordUnchanged() throws Exception {
        var admin = account(true); var target = account(false);
        String passwordHash = target.getPasswordHash();
        var reset = tokens.issue(target.getId(), TokenType.PASSWORD_RESET);
        tokens.issue(target.getId(), TokenType.EMAIL_VERIFICATION);
        tokens.issueEmailChange(target.getId(), "previous@example.invalid");
        String username = "Renamed" + target.getId();
        try (var a = new Client(); var learner = new Client()) {
            a.login(admin); learner.login(target);
            String body = body("  " + username + "  ", "  " + target.getEmail().toUpperCase() + "  ", "ADMIN", "USER");
            var response = a.edit(target.getId(), body, true);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var data = json.readTree(response.body());
            assertEquals(username, data.get("user").get("username").asText());
            assertEquals(2, data.get("user").get("roles").size());
            assertTrue(data.get("sessionsInvalidated").asBoolean());
            assertTrue(data.get("pendingEmail").isNull());
            assertFalse(response.body().contains("password"));
            assertEquals(401, learner.get("/api/auth/me").statusCode());
            var saved = users.findById(target.getId()).orElseThrow();
            assertEquals(passwordHash, saved.getPasswordHash());
            assertEquals(1, saved.getSessionVersion());
            for (var type : TokenType.values()) assertTrue(storedTokens.findFirstByUser_IdAndTypeOrderByCreatedAtDesc(target.getId(), type).isEmpty());
            assertThrows(InvalidTokenException.class, () -> tokens.consume(reset.value(), TokenType.PASSWORD_RESET));
            var repeated = a.edit(target.getId(), body, true);
            assertEquals(200, repeated.statusCode());
            assertFalse(json.readTree(repeated.body()).get("sessionsInvalidated").asBoolean());
            assertEquals(1, users.findById(target.getId()).orElseThrow().getSessionVersion());
            learner.login(saved);
            assertEquals(204, learner.get("/api/admin/access").statusCode());
            assertTrue(roles.findByName(RoleName.ADMIN).isPresent());
            assertTrue(roles.findByName(RoleName.USER).isPresent());
            verifyNoInteractions(publisher);
        }
    }

    @Test void keepsCurrentEmailUntilConfirmationAndMakesImmediateRetriesIdempotent() throws Exception {
        var admin = account(true); var target = account(false);
        var reset = tokens.issue(target.getId(), TokenType.PASSWORD_RESET);
        var oldChange = tokens.issueEmailChange(target.getId(), "older@example.invalid");
        String nextEmail = "new" + target.getId() + "@example.invalid";
        String body = body(target.getUsername(), nextEmail, "USER");
        try (var a = new Client(); var learner = new Client(); var confirmation = new Client()) {
            a.login(admin); learner.login(target);
            var response = a.edit(target.getId(), body, true);
            assertEquals(200, response.statusCode(), response.body());
            var data = json.readTree(response.body());
            assertEquals(target.getEmail(), data.get("user").get("email").asText());
            assertEquals(nextEmail, data.get("pendingEmail").asText());
            assertFalse(data.get("sessionsInvalidated").asBoolean());
            assertEquals(200, learner.get("/api/auth/me").statusCode());
            assertEquals(target.getEmail(), users.findById(target.getId()).orElseThrow().getEmail());
            assertEquals(200, a.edit(target.getId(), body, true).statusCode());
            var capture = ArgumentCaptor.forClass(EmailChangeRequested.class);
            verify(publisher, times(1)).publish(capture.capture());
            assertEquals(nextEmail, capture.getValue().recipient());
            assertThrows(InvalidTokenException.class, () -> tokens.consumeEmailChange(oldChange.value()));
            var confirmed = confirmation.post("/api/auth/confirm-email-change", Map.of("token", capture.getValue().token()));
            assertEquals(200, confirmed.statusCode(), confirmed.body());
            assertEquals(nextEmail, users.findById(target.getId()).orElseThrow().getEmail());
            assertEquals(401, learner.get("/api/auth/me").statusCode());
            assertThrows(InvalidTokenException.class, () -> tokens.consume(reset.value(), TokenType.PASSWORD_RESET));
        }
    }

    @Test void rejectsUnauthorizedInvalidDuplicateAndLastAdministratorChangesAtomically() throws Exception {
        var admin = account(true); var target = account(false); var duplicate = account(false);
        var reset = tokens.issue(target.getId(), TokenType.PASSWORD_RESET);
        try (var a = new Client(); var learner = new Client(); var anonymous = new Client()) {
            String valid = body(target.getUsername(), target.getEmail(), "ADMIN", "USER");
            assertEquals(401, anonymous.edit(target.getId(), valid, true).statusCode());
            learner.login(target); assertEquals(403, learner.edit(target.getId(), valid, true).statusCode());
            a.login(admin); assertEquals(403, a.edit(target.getId(), valid, false).statusCode());
            var self = a.edit(admin.getId(), body(admin.getUsername(), admin.getEmail(), "USER"), true);
            assertEquals(409, self.statusCode());
            assertEquals("You cannot remove the last active administrator role.", json.readTree(self.body()).get("fieldErrors").get("roles").asText());
            var duplicateName = a.edit(target.getId(), body(duplicate.getUsername().toUpperCase(), target.getEmail(), "ADMIN"), true);
            assertEquals(409, duplicateName.statusCode());
            assertTrue(json.readTree(duplicateName.body()).get("fieldErrors").has("username"));
            var duplicateEmail = a.edit(target.getId(), body("ChangedName", duplicate.getEmail().toUpperCase(), "ADMIN"), true);
            assertEquals(409, duplicateEmail.statusCode());
            assertTrue(json.readTree(duplicateEmail.body()).get("fieldErrors").has("email"));
            assertEquals(target.getUsername(), users.findById(target.getId()).orElseThrow().getUsername());
            assertEquals(0, users.findById(target.getId()).orElseThrow().getSessionVersion());
            assertFalse(users.findByUsernameIgnoreCase(target.getUsername()).orElseThrow().getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN));
            assertTrue(storedTokens.findFirstByUser_IdAndTypeOrderByCreatedAtDesc(target.getId(), TokenType.PASSWORD_RESET).isPresent());
            for (String invalid : new String[]{body("ab", target.getEmail(), "USER"), body("bad name", target.getEmail(), "USER"),
                    body("ValidName", "not-an-email", "USER"), body("ValidName", target.getEmail()), body("ValidName", target.getEmail(), "ROOT"),
                    "{\"username\":\"ValidName\",\"email\":\"valid@example.invalid\",\"roles\":null}"}) {
                assertEquals(400, a.edit(target.getId(), invalid, true).statusCode(), invalid);
            }
            assertEquals(404, a.edit(Long.MAX_VALUE, valid, true).statusCode());
            assertEquals(1, users.countByEnabledTrueAndEmailVerifiedAtIsNotNullAndRoles_Name(RoleName.ADMIN));
            assertEquals(200, learner.get("/api/auth/me").statusCode());
            verifyNoInteractions(publisher);
        }
    }

    @Test void competingDemotionsLeaveOneAdministratorAndRevokeTheDemotedSession() throws Exception {
        var first = account(true); var second = account(true);
        try (var a = new Client(); var b = new Client()) {
            a.login(first); b.login(second);
            String tokenA = a.csrf(); String tokenB = b.csrf();
            String firstBody = body(second.getUsername(), second.getEmail(), "USER");
            String secondBody = body(first.getUsername(), first.getEmail(), "USER");
            var x = CompletableFuture.supplyAsync(() -> editStatus(a, second.getId(), firstBody, tokenA));
            var y = CompletableFuture.supplyAsync(() -> editStatus(b, first.getId(), secondBody, tokenB));
            int resultA = x.join(); int resultB = y.join();
            assertTrue(resultA == 200 && (resultB == 401 || resultB == 403) || resultB == 200 && (resultA == 401 || resultA == 403), resultA + "/" + resultB);
            assertEquals(1, users.countByEnabledTrueAndEmailVerifiedAtIsNotNullAndRoles_Name(RoleName.ADMIN));
            assertEquals(401, (resultA == 200 ? b : a).get("/api/admin/access").statusCode());
        }
    }

    @Test void confirmsAdminRequestedEmailForUnverifiedAccountAndAllowsSelfDemotionWithAnotherAdmin() throws Exception {
        var admin = account(true); account(true); var target = account(false);
        target.setEmailVerifiedAt(null); users.saveAndFlush(target);
        String nextEmail = "verify" + target.getId() + "@example.invalid";
        try (var a = new Client(); var confirmation = new Client()) {
            a.login(admin);
            assertEquals(200, a.edit(target.getId(), body(target.getUsername(), nextEmail, "USER"), true).statusCode());
            assertNull(users.findById(target.getId()).orElseThrow().getEmailVerifiedAt());
            assertEquals(target.getEmail(), users.findById(target.getId()).orElseThrow().getEmail());
            var capture = ArgumentCaptor.forClass(EmailChangeRequested.class);
            verify(publisher).publish(capture.capture());
            assertEquals(200, confirmation.post("/api/auth/confirm-email-change", Map.of("token", capture.getValue().token())).statusCode());
            var verified = users.findById(target.getId()).orElseThrow();
            assertEquals(nextEmail, verified.getEmail()); assertNotNull(verified.getEmailVerifiedAt());
            var response = a.edit(admin.getId(), body(admin.getUsername(), admin.getEmail(), "USER"), true);
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(json.readTree(response.body()).get("sessionsInvalidated").asBoolean());
            assertEquals(401, a.get("/api/admin/access").statusCode());
            a.login(admin); assertEquals(403, a.get("/api/admin/access").statusCode());
        }
    }

    private int editStatus(Client client, Long id, String body, String token) {
        try { return client.editWithToken(id, body, token).statusCode(); }
        catch (Exception exception) { throw new CompletionException(exception); }
    }
    private String body(String username, String email, String... roles) throws Exception {
        return json.writeValueAsString(Map.of("username", username, "email", email, "roles", List.of(roles)));
    }
    private User account(boolean admin) {
        String name = "AdminEdit" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(PASSWORD));
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        if (admin) user.addRole(roles.findByName(RoleName.ADMIN).orElseThrow());
        user.setEmailVerifiedAt(Instant.now()); return users.saveAndFlush(user);
    }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception { return http.send(HttpRequest.newBuilder(endpoint(path)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        String csrf() throws Exception { var response = get("/api/auth/csrf"); assertEquals(200, response.statusCode()); return json.readTree(response.body()).get("token").asText(); }
        void login(User user) throws Exception {
            var response = post("/api/auth/login", Map.of("login", user.getUsername(), "password", PASSWORD)); assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).timeout(Duration.ofSeconds(15)).header("X-CSRF-TOKEN", csrf()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> edit(Long id, String body, boolean withToken) throws Exception { return editWithToken(id, body, withToken ? csrf() : null); }
        HttpResponse<String> editWithToken(Long id, String body, String token) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/admin/users/" + id)).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return http.send(request.PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
