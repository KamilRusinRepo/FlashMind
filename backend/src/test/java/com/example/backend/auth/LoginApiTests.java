package com.example.backend.auth;

import com.example.backend.support.PostgresIntegrationTest;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"})
@Import(LoginApiTests.ProbeConfiguration.class)
class LoginApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "  ExamplePassword123!  ";
    @Autowired Environment environment;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;

    @Test
    void loginByUsernamePersistsAuthenticationAndRolesAcrossRequestsWithoutExposingCredentials() throws Exception {
        User user = account(true, true, RoleName.USER);
        try (var client = new SessionClient()) {
            assertEquals(401, client.get("/api/login-test/protected").statusCode());
            var response = client.post("/api/auth/login", Map.of("login", " " + user.getUsername().toUpperCase() + " ", "password", PASSWORD));
            assertEquals(200, response.statusCode());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var body = json.readTree(response.body());
            assertEquals(user.getId().longValue(), body.get("id").asLong());
            assertEquals(user.getUsername(), body.get("username").asText());
            assertEquals(user.getEmail(), body.get("email").asText());
            assertEquals("USER", body.get("roles").get(0).asText());
            assertFalse(response.body().contains(PASSWORD));
            assertFalse(response.body().contains(user.getPasswordHash()));
            assertFalse(body.has("token"));
            assertFalse(body.has("password"));

            var protectedResponse = client.get("/api/login-test/protected");
            assertEquals(200, protectedResponse.statusCode());
            var identity = json.readTree(protectedResponse.body());
            assertEquals(user.getUsername(), identity.get("username").asText());
            assertEquals("SessionUser", identity.get("principalType").asText());
            assertFalse(identity.get("credentialsPresent").asBoolean());
            assertEquals(200, client.get("/api/login-test/protected").statusCode());
        }
        try (var otherBrowser = new SessionClient()) {
            assertEquals(401, otherBrowser.get("/api/login-test/protected").statusCode());
        }
    }

    @Test
    void loginByEmailSupportsAdministratorWithActualDatabaseRoles() throws Exception {
        User user = account(true, true, RoleName.USER, RoleName.ADMIN);
        try (var client = new SessionClient()) {
            var response = client.post("/api/auth/login", Map.of("login", user.getEmail().toUpperCase(), "password", PASSWORD));
            assertEquals(200, response.statusCode());
            var actualRoles = json.readTree(response.body()).get("roles");
            assertEquals(List.of("ADMIN", "USER"), json.convertValue(actualRoles, List.class));
            assertEquals(200, client.get("/api/login-test/protected").statusCode());
        }
    }

    @Test
    void successfulLoginChangesSessionIdInvalidatesOldSessionAndRotatesCsrf() throws Exception {
        User user = account(true, true, RoleName.USER);
        try (var client = new SessionClient()) {
            String oldCsrf = client.csrf();
            String oldSession = client.sessionId();
            assertEquals(200, client.postWithToken("/api/auth/login",
                    Map.of("login", user.getUsername(), "password", PASSWORD), oldCsrf).statusCode());
            assertNotEquals(oldSession, client.sessionId());
            assertEquals(403, client.postWithToken("/api/login-test/mutate", Map.of(), oldCsrf).statusCode());
            assertEquals(200, client.post("/api/login-test/mutate", Map.of()).statusCode());
            try (var oldBrowser = HttpClient.newHttpClient()) {
                var response = oldBrowser.send(HttpRequest.newBuilder(endpoint("/api/login-test/protected"))
                        .header("Cookie", "JSESSIONID=" + oldSession).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(401, response.statusCode());
            }
        }
    }

    @Test
    void unknownWrongPasswordAndBlockedAccountReturnTheSameSafe401AndNoAuthentication() throws Exception {
        User user = account(true, true, RoleName.USER);
        User blocked = account(false, true, RoleName.USER);
        String expected = null;
        for (Map<String, String> attempt : List.of(Map.of("login", "missing_" + UUID.randomUUID(), "password", PASSWORD),
                Map.of("login", user.getUsername(), "password", "wrong-password"),
                Map.of("login", blocked.getUsername(), "password", PASSWORD))) {
            try (var client = new SessionClient()) {
                var response = client.post("/api/auth/login", attempt);
                assertEquals(401, response.statusCode());
                var body = json.readTree(response.body());
                String message = body.get("message").asText();
                if (expected == null) expected = message;
                assertEquals(expected, message);
                assertFalse(response.body().contains(attempt.get("password")));
                assertEquals(401, client.get("/api/login-test/protected").statusCode());
            }
        }
    }

    @Test
    void unverifiedStatusIsReportedOnlyForCorrectPasswordWithoutCreatingAuthentication() throws Exception {
        User user = account(true, false, RoleName.USER);
        try (var client = new SessionClient()) {
            var wrongPassword = client.post("/api/auth/login", Map.of("login", user.getUsername(), "password", "wrong-password"));
            assertEquals(401, wrongPassword.statusCode());
            assertFalse(json.readTree(wrongPassword.body()).has("code"));
            assertFalse(json.readTree(wrongPassword.body()).has("email"));
            var response = client.post("/api/auth/login", Map.of("login", user.getUsername(), "password", PASSWORD));
            assertEquals(403, response.statusCode());
            assertEquals("Confirm your email address before signing in.", json.readTree(response.body()).get("message").asText());
            assertEquals("EMAIL_VERIFICATION_REQUIRED", json.readTree(response.body()).get("code").asText());
            assertEquals(user.getEmail(), json.readTree(response.body()).get("email").asText());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(401, client.get("/api/login-test/protected").statusCode());
        }
    }

    @Test
    void requestValidationAndPasswordByteLimitUseApiErrorsWithoutEchoingPassword() throws Exception {
        try (var client = new SessionClient()) {
            var empty = client.post("/api/auth/login", Map.of());
            assertEquals(400, empty.statusCode());
            assertTrue(json.readTree(empty.body()).get("fieldErrors").has("login"));
            assertTrue(json.readTree(empty.body()).get("fieldErrors").has("password"));
            String tooLong = "\u0105".repeat(40);
            var response = client.post("/api/auth/login", Map.of("login", "learner", "password", tooLong));
            assertEquals(400, response.statusCode());
            assertTrue(json.readTree(response.body()).get("fieldErrors").has("password"));
            assertFalse(response.body().contains(tooLong));
            assertEquals(401, client.get("/api/login-test/protected").statusCode());
        }
    }

    @Test
    void loginRequiresCsrfAndDoesNotAcceptGet() throws Exception {
        try (var client = new SessionClient()) {
            assertEquals(403, client.postWithToken("/api/auth/login", Map.of("login", "learner", "password", PASSWORD), "wrong-csrf").statusCode());
            assertEquals(405, client.get("/api/auth/login").statusCode());
            assertEquals(401, client.get("/api/login-test/protected").statusCode());
        }
    }

    private User account(boolean enabled, boolean verified, RoleName... roleNames) {
        String name = "login_" + UUID.randomUUID().toString().replace("-", "");
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.setEnabled(enabled);
        if (verified) user.setEmailVerifiedAt(Instant.now());
        for (var role : roleNames) user.addRole(roles.findByName(role).orElseThrow());
        return users.saveAndFlush(user);
    }

    private URI endpoint(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }

    private class SessionClient implements AutoCloseable {
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
        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
        }
        HttpResponse<String> post(String path, Map<String, ?> body) throws Exception { return postWithToken(path, body, csrf()); }
        HttpResponse<String> postWithToken(String path, Map<String, ?> body, String csrf) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", csrf).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }

    @TestConfiguration
    static class ProbeConfiguration { @Bean ProbeController loginProbe() { return new ProbeController(); } }

    @RestController
    static class ProbeController {
        @GetMapping("/api/login-test/protected")
        Map<String, Object> identity(Authentication authentication) {
            return Map.of("username", authentication.getName(), "principalType", authentication.getPrincipal().getClass().getSimpleName(),
                    "credentialsPresent", authentication.getCredentials() != null);
        }
        @PostMapping("/api/login-test/mutate")
        Map<String, Boolean> mutate() { return Map.of("authenticated", true); }
    }
}
