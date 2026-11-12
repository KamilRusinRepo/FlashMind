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

import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"})
class SessionApiTests {
    private static final String PASSWORD = "SessionPassword123!";
    @Autowired Environment environment;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;

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
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", csrf()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            assertEquals(200, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
        }
        HttpResponse<String> logout() throws Exception { return postWithToken("/api/auth/logout", csrf()); }
        HttpResponse<String> postWithToken(String path, String token) throws Exception {
            var request = HttpRequest.newBuilder(endpoint(path));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return http.send(request.POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
