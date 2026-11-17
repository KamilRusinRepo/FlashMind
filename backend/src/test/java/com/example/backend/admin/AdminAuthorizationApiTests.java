package com.example.backend.admin;

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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthorizationApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "AdminAccessTests123!";
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test void anonymousRequestsReturnJson401AcrossTheAdminNamespace() throws Exception {
        try (var client = new Client()) {
            for (String path : new String[]{"/api/admin", "/api/admin/access", "/api/admin/users", "/api/admin/decks"}) {
                var response = client.get(path);
                assertEquals(401, response.statusCode(), path);
                var error = json.readTree(response.body());
                assertEquals(401, error.get("status").asInt());
                assertEquals(path, error.get("path").asText());
                assertTrue(error.get("fieldErrors").isObject());
                assertTrue(response.headers().firstValue("Location").isEmpty());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            }
        }
    }

    @Test void userCannotAccessAdminRoutesOrForgeAnAdminRoleInHeaders() throws Exception {
        try (var client = new Client()) {
            client.login(account(false));
            for (String path : new String[]{"/api/admin", "/api/admin/access", "/api/admin/users", "/api/admin/decks"}) {
                var response = client.get(path);
                assertEquals(403, response.statusCode(), response.body());
                assertEquals("You do not have permission to access this resource.", json.readTree(response.body()).get("message").asText());
            }
            var forged = client.http.send(HttpRequest.newBuilder(endpoint("/api/admin/access")).header("X-Role", "ADMIN")
                    .header("X-Roles", "ROLE_ADMIN").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, forged.statusCode());
            for (String method : new String[]{"POST", "PUT", "DELETE"}) {
                assertEquals(403, client.mutate(method, "/api/admin/users/123", true).statusCode());
            }
            assertEquals(200, client.get("/api/auth/me").statusCode());
            assertEquals(200, client.get("/api/decks").statusCode());
        }
    }

    @Test void adminCanCheckAccessButMutationsStillRequireCsrf() throws Exception {
        try (var client = new Client()) {
            client.login(account(true));
            var response = client.get("/api/admin/access");
            assertEquals(204, response.statusCode());
            assertEquals("", response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var withoutToken = client.mutate("POST", "/api/admin/access", false);
            assertEquals(403, withoutToken.statusCode());
            assertTrue(json.readTree(withoutToken.body()).get("message").asText().contains("CSRF"));
            // With a valid token, authorization passes and MVC rejects the unsupported HTTP method.
            assertEquals(405, client.mutate("POST", "/api/admin/access", true).statusCode());
        }
    }

    @Test void invalidatingSessionsAfterRoleRemovalRevokesExistingAdminCookies() throws Exception {
        var user = account(true);
        try (var client = new Client()) {
            client.login(user);
            assertEquals(204, client.get("/api/admin/access").statusCode());
            user.removeRole(user.getRoles().stream().filter(role -> role.getName() == RoleName.ADMIN).findFirst().orElseThrow());
            user.invalidateSessions();
            users.saveAndFlush(user);
            assertEquals(401, client.get("/api/admin/access").statusCode());
            client.login(user);
            assertEquals(403, client.get("/api/admin/access").statusCode());
        }
    }

    private User account(boolean admin) {
        String name = "AdminTest" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        if (admin) user.addRole(roles.findByName(RoleName.ADMIN).orElseThrow());
        return users.saveAndFlush(user);
    }

    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }

    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        String csrf() throws Exception {
            var response = get("/api/auth/csrf"); assertEquals(200, response.statusCode());
            return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).header("X-CSRF-TOKEN", csrf())
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString()); assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> mutate(String method, String path, boolean withToken) throws Exception {
            var request = HttpRequest.newBuilder(endpoint(path)).header("Content-Type", "application/json");
            if (withToken) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.method(method, HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
