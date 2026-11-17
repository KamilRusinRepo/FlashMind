package com.example.backend.auth;

import com.example.backend.support.PostgresIntegrationTest;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.InvalidTokenException;
import com.example.backend.user.PasswordChangeService;
import com.example.backend.user.EmailChangeService;
import com.example.backend.user.dto.request.ChangePasswordRequest;
import com.example.backend.user.dto.request.RequestEmailChange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"})
class SessionApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "SessionPassword123!";
    @Autowired Environment environment;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired TokenService tokens;
    @Autowired PasswordChangeService passwordChanges;
    @Autowired EmailChangeService emailChanges;

    @Test
    void anonymousMeReturns401EvenAfterObtainingCsrf() throws Exception {
        try (var client = new Client()) {
            assertEquals(401, client.get("/api/auth/me").statusCode());
            client.csrf();
            var response = client.get("/api/auth/me");
            assertEquals(401, response.statusCode());
            assertEquals(401, json.readTree(response.body()).get("status").asInt());
            assertFalse(json.readTree(response.body()).has("username"));
        }
    }

    @Test
    void meReturnsSessionOwnerAndRolesWithoutCredentialsOrTokens() throws Exception {
        User owner = account();
        User other = account();
        try (var client = new Client()) {
            client.login(owner);
            var response = client.get("/api/auth/me");
            assertEquals(200, response.statusCode());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var body = json.readTree(response.body());
            assertEquals(owner.getId().longValue(), body.get("id").asLong());
            assertEquals(owner.getUsername(), body.get("username").asText());
            assertEquals(owner.getEmail(), body.get("email").asText());
            assertEquals("USER", body.get("roles").get(0).asText());
            assertFalse(response.body().contains(other.getUsername()));
            assertFalse(response.body().contains(owner.getPasswordHash()));
            assertFalse(response.body().contains(PASSWORD));
            assertEquals(4, body.size());
            assertEquals(200, client.get("/api/auth/me").statusCode());
        }
    }

    @Test
    void logoutInvalidatesSessionDeletesCookieAndRejectsOldSessionAndCsrf() throws Exception {
        try (var client = new Client()) {
            client.login(account());
            String oldCsrf = client.csrf();
            String oldSession = client.sessionId();
            var response = client.postWithToken("/api/auth/logout", oldCsrf);
            assertEquals(204, response.statusCode());
            assertEquals("", response.body());
            assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
            assertTrue(client.cookies.getCookieStore().getCookies().stream().noneMatch(cookie -> cookie.getName().equals("JSESSIONID")));
            assertEquals(401, client.get("/api/auth/me").statusCode());
            assertEquals(403, client.postWithToken("/api/auth/logout", oldCsrf).statusCode());
            try (var oldBrowser = HttpClient.newHttpClient()) {
                var stale = oldBrowser.send(HttpRequest.newBuilder(endpoint("/api/auth/me"))
                        .header("Cookie", "JSESSIONID=" + oldSession).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(401, stale.statusCode());
            }
        }
    }

    @Test
    void missingOrInvalidCsrfCannotLogAUserOut() throws Exception {
        try (var client = new Client()) {
            client.login(account());
            assertEquals(403, client.postWithToken("/api/auth/logout", null).statusCode());
            assertEquals(200, client.get("/api/auth/me").statusCode());
            assertEquals(403, client.postWithToken("/api/auth/logout", "bad-token").statusCode());
            assertEquals(200, client.get("/api/auth/me").statusCode());
        }
    }

    @Test
    void getLogoutDoesNotInvalidateSession() throws Exception {
        try (var client = new Client()) {
            client.login(account());
            assertNotEquals(204, client.get("/api/auth/logout").statusCode());
            assertEquals(200, client.get("/api/auth/me").statusCode());
        }
    }

    @Test
    void logoutIsIdempotentForAnonymousOrAlreadyExpiredSessionWithValidCsrf() throws Exception {
        try (var client = new Client()) {
            assertEquals(204, client.logout().statusCode());
            assertEquals(204, client.logout().statusCode());
            assertEquals(401, client.get("/api/auth/me").statusCode());
        }
    }

    @Test
    void logoutDoesNotInvalidateAnotherBrowserSession() throws Exception {
        User owner = account();
        try (var first = new Client(); var second = new Client()) {
            first.login(owner);
            second.login(owner);
            assertEquals(204, first.logout().statusCode());
            assertEquals(401, first.get("/api/auth/me").statusCode());
            assertEquals(200, second.get("/api/auth/me").statusCode());
        }
    }

    @Test
    void loginAndProtectedCsrfFlowWorkAgainAfterLogout() throws Exception {
        User owner = account();
        try (var client = new Client()) {
            client.login(owner);
            assertEquals(204, client.logout().statusCode());
            client.login(owner);
            assertEquals(200, client.get("/api/auth/me").statusCode());
            assertEquals(204, client.logout().statusCode());
        }
    }

    @Test
    void usernameChangePersistsAndMeReadsItInExistingSessionsWithoutChangingOtherAccountData() throws Exception {
        var owner = account();
        var other = account();
        String newName = "Updated_" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        try (var client = new Client(); var secondBrowser = new Client()) {
            client.login(owner);
            secondBrowser.login(owner);
            var response = client.updateUsername(Map.of("username", "  " + newName + "  ", "email", "ignored@example.invalid", "id", other.getId(), "roles", new String[]{"ADMIN"}), true);
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            var body = json.readTree(response.body());
            assertEquals(newName, body.get("username").asText());
            assertEquals(owner.getEmail(), body.get("email").asText());
            assertEquals("USER", body.get("roles").get(0).asText());
            assertEquals(4, body.size());
            assertEquals(newName, json.readTree(client.get("/api/auth/me").body()).get("username").asText());
            assertEquals(newName, json.readTree(secondBrowser.get("/api/auth/me").body()).get("username").asText());
            assertEquals(200, client.updateUsername(Map.of("username", newName), true).statusCode());
            assertEquals(200, client.updateUsername(Map.of("username", newName.toLowerCase(java.util.Locale.ROOT)), true).statusCode());
            var stored = users.findById(owner.getId()).orElseThrow();
            assertEquals(newName.toLowerCase(java.util.Locale.ROOT), stored.getUsername());
            assertEquals(owner.getPasswordHash(), stored.getPasswordHash());
            assertEquals(owner.getEmail(), stored.getEmail());
            assertEquals(owner.getSessionVersion(), stored.getSessionVersion());
            assertEquals(other.getUsername(), users.findById(other.getId()).orElseThrow().getUsername());
            assertEquals(204, client.logout().statusCode());
            owner.setUsername(stored.getUsername());
            client.login(owner); // The new name is now the login identifier.
        }
    }

    @Test
    void usernameChangeRequiresAuthenticationCsrfValidInputAndAvailableName() throws Exception {
        var owner = account();
        var other = account();
        try (var client = new Client()) {
            assertEquals(401, client.updateUsername(Map.of("username", "valid_name"), true).statusCode());
            client.login(owner);
            assertEquals(403, client.updateUsername(Map.of("username", "valid_name"), false).statusCode());
            for (String value : new String[]{"", "  ", "ab", "a".repeat(51), "name with spaces", "invalid!"}) {
                var response = client.updateUsername(Map.of("username", value), true);
                assertEquals(400, response.statusCode(), response.body());
                assertTrue(json.readTree(response.body()).get("fieldErrors").has("username"));
            }
            assertEquals(400, client.updateUsername(Map.of(), true).statusCode());
            var conflict = client.updateUsername(Map.of("username", other.getUsername().toUpperCase(java.util.Locale.ROOT)), true);
            assertEquals(409, conflict.statusCode(), conflict.body());
            assertEquals("This username is already taken.", json.readTree(conflict.body()).get("fieldErrors").get("username").asText());
            assertEquals(owner.getUsername(), users.findById(owner.getId()).orElseThrow().getUsername());
        }
    }

    @Test
    void concurrentAccountsCannotClaimTheSameUsername() throws Exception {
        var firstUser = account();
        var secondUser = account();
        String name = "Claim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        try (var first = new Client(); var second = new Client()) {
            first.login(firstUser);
            second.login(secondUser);
            first.csrf();
            second.csrf();
            var firstRequest = java.util.concurrent.CompletableFuture.supplyAsync(() -> first.claimUsername(name));
            var secondRequest = java.util.concurrent.CompletableFuture.supplyAsync(() -> second.claimUsername(name));
            var one = firstRequest.get(20, java.util.concurrent.TimeUnit.SECONDS);
            var two = secondRequest.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue((one.statusCode() == 200 && two.statusCode() == 409) || (one.statusCode() == 409 && two.statusCode() == 200));
            var failed = one.statusCode() == 409 ? one : two;
            assertEquals("This username is already taken.", json.readTree(failed.body()).get("fieldErrors").get("username").asText());
        }
    }

    @Test
    void passwordChangeRevokesAllOwnerSessionsAndPendingLinksButPreservesOtherAccounts() throws Exception {
        var owner = account();
        var other = account();
        var emailChange = tokens.issueEmailChange(owner.getId(), "pending@example.invalid");
        var reset = tokens.issue(owner.getId(), TokenType.PASSWORD_RESET);
        var otherReset = tokens.issue(other.getId(), TokenType.PASSWORD_RESET);
        String newPassword = "  UpdatedPassword123!  ";
        try (var client = new Client(); var second = new Client(); var unrelated = new Client()) {
            client.login(owner); second.login(owner); unrelated.login(other);
            var response = client.changePassword(Map.of("currentPassword", PASSWORD, "newPassword", newPassword, "confirmPassword", newPassword, "id", other.getId()), true);
            assertEquals(204, response.statusCode(), response.body());
            assertEquals("", response.body());
            assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            assertTrue(client.cookies.getCookieStore().getCookies().stream().noneMatch(cookie -> cookie.getName().equals("JSESSIONID")));
            assertEquals(401, client.get("/api/auth/me").statusCode());
            assertEquals(401, second.get("/api/auth/me").statusCode());
            assertEquals(200, unrelated.get("/api/auth/me").statusCode());
            var stored = users.findById(owner.getId()).orElseThrow();
            assertTrue(encoder.matches(newPassword, stored.getPasswordHash()));
            assertFalse(encoder.matches(newPassword.trim(), stored.getPasswordHash()));
            assertEquals(owner.getSessionVersion() + 1, stored.getSessionVersion());
            assertEquals(owner.getUsername(), stored.getUsername());
            assertEquals(owner.getEmail(), stored.getEmail());
            assertEquals(other.getPasswordHash(), users.findById(other.getId()).orElseThrow().getPasswordHash());
            assertThrows(InvalidTokenException.class, () -> tokens.consumeEmailChange(emailChange.value()));
            assertThrows(InvalidTokenException.class, () -> tokens.consume(reset.value(), TokenType.PASSWORD_RESET));
            assertEquals(other.getId(), tokens.consume(otherReset.value(), TokenType.PASSWORD_RESET).getId());
            assertEquals(401, client.loginResponse(owner.getUsername(), PASSWORD).statusCode());
            assertEquals(200, client.loginResponse(owner.getUsername(), newPassword).statusCode());
        }
    }

    @Test
    void invalidPasswordChangesLeaveHashSessionsAndPendingEmailIntact() throws Exception {
        var owner = account();
        var pending = tokens.issueEmailChange(owner.getId(), "pending@example.invalid");
        try (var client = new Client()) {
            var valid = Map.of("currentPassword", PASSWORD, "newPassword", "UpdatedPassword123!", "confirmPassword", "UpdatedPassword123!");
            assertEquals(401, client.changePassword(valid, true).statusCode());
            client.login(owner);
            assertEquals(403, client.changePassword(valid, false).statusCode());
            var wrong = client.changePassword(Map.of("currentPassword", "incorrect", "newPassword", "UpdatedPassword123!", "confirmPassword", "UpdatedPassword123!"), true);
            assertEquals(400, wrong.statusCode(), wrong.body());
            assertTrue(json.readTree(wrong.body()).get("fieldErrors").has("currentPassword"));
            var mismatch = client.changePassword(Map.of("currentPassword", PASSWORD, "newPassword", "UpdatedPassword123!", "confirmPassword", "different"), true);
            assertEquals(400, mismatch.statusCode());
            assertTrue(json.readTree(mismatch.body()).get("fieldErrors").has("confirmPassword"));
            for (String invalid : new String[]{"", "short", "a".repeat(73), "ą".repeat(37)}) {
                var response = client.changePassword(Map.of("currentPassword", PASSWORD, "newPassword", invalid, "confirmPassword", invalid), true);
                assertEquals(400, response.statusCode(), response.body());
                assertTrue(json.readTree(response.body()).get("fieldErrors").has("newPassword"));
            }
            var stored = users.findById(owner.getId()).orElseThrow();
            assertEquals(owner.getPasswordHash(), stored.getPasswordHash());
            assertEquals(owner.getSessionVersion(), stored.getSessionVersion());
            assertEquals(200, client.get("/api/auth/me").statusCode());
            assertNotNull(tokens.consumeEmailChange(pending.value())); // Failed attempts did not cancel it.
        }
    }

    @Test
    void staleSessionCannotChangePasswordOrCreatePendingEmailAfterPasswordChange() {
        var owner = account();
        var stale = new SessionUser(owner.getId(), owner.getUsername(), owner.getEmail(), java.util.List.of("USER"), owner.getSessionVersion());
        // The same password also exercises version checking independently of BCrypt matching.
        var request = new ChangePasswordRequest(PASSWORD, PASSWORD, PASSWORD);
        passwordChanges.change(stale, request);
        var passwordError = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> passwordChanges.change(stale, request));
        assertEquals(401, passwordError.getStatusCode().value());
        var emailError = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> emailChanges.request(owner.getId(), stale.sessionVersion(), new RequestEmailChange("later@example.invalid", PASSWORD)));
        assertEquals(401, emailError.getStatusCode().value());
        assertEquals(owner.getSessionVersion() + 1, users.findById(owner.getId()).orElseThrow().getSessionVersion());
    }

    private User account() {
        String name = "session_" + UUID.randomUUID().toString().replace("-", "");
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        return users.saveAndFlush(user);
    }

    private URI endpoint(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }

    private class Client implements AutoCloseable {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        String csrf() throws Exception {
            var response = get("/api/auth/csrf");
            assertEquals(200, response.statusCode());
            return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            assertEquals(200, loginResponse(user.getUsername(), PASSWORD).statusCode());
        }
        HttpResponse<String> loginResponse(String login, String password) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", csrf()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            Map.of("login", login, "password", password)))).build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> changePassword(Map<String, ?> body, boolean protectedRequest) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/users/me/password")).header("Content-Type", "application/json");
            if (protectedRequest) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
        }
        HttpResponse<String> logout() throws Exception { return postWithToken("/api/auth/logout", csrf()); }
        HttpResponse<String> updateUsername(Map<String, ?> body, boolean protectedRequest) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/users/me/username")).header("Content-Type", "application/json");
            if (protectedRequest) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> claimUsername(String username) {
            try { return updateUsername(Map.of("username", username), true); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        }
        HttpResponse<String> postWithToken(String path, String token) throws Exception {
            var request = HttpRequest.newBuilder(endpoint(path));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return http.send(request.POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
