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
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "AdminUserTests123!";
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test void listsSafeDtoWithFiltersLiteralSearchPaginationAndGlobalSummary() throws Exception {
        var admin = account("Admin", true);
        var active = account("Active_under", false);
        var blocked = account("Blocked", false);
        blocked.setEnabled(false); users.saveAndFlush(blocked);
        var pending = account("Pending", false);
        pending.setEmailVerifiedAt(null); users.saveAndFlush(pending);
        var secondAdmin = account("SecondAdmin", true);
        try (var client = new Client()) {
            assertEquals(401, client.get("/api/admin/users").statusCode());
            client.login(admin);
            var response = client.get("/api/admin/users?size=2");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var page = json.readTree(response.body());
            assertEquals(5, page.get("totalElements").asInt());
            assertEquals(3, page.get("totalPages").asInt());
            assertEquals(2, page.get("content").size());
            assertEquals(secondAdmin.getId().longValue(), page.get("content").get(0).get("id").asLong());
            assertEquals(2, page.get("content").get(0).get("roles").size());
            assertEquals(5, page.get("summary").get("users").asInt());
            assertEquals(4, page.get("summary").get("activeUsers").asInt());
            assertEquals(1, page.get("summary").get("blockedUsers").asInt());
            assertEquals(0, page.get("summary").get("publicDecks").asInt());
            assertFalse(response.body().contains("password"));
            assertFalse(response.body().contains("sessionVersion"));
            assertEquals(1, client.page("?size=2&page=2").get("content").size());
            assertEquals(0, client.page("?page=99").get("content").size());
            var filtered = client.page("?status=blocked&role=USER");
            assertEquals(1, filtered.get("totalElements").asInt());
            assertEquals(blocked.getId().longValue(), filtered.get("content").get(0).get("id").asLong());
            assertEquals(5, filtered.get("summary").get("users").asInt());
            assertEquals(2, client.page("?role=ADMIN").get("totalElements").asInt());
            assertEquals(active.getId().longValue(), client.page("?search=" + active.getEmail().toUpperCase()).get("content").get(0).get("id").asLong());
            assertEquals(1, client.page("?search=_").get("totalElements").asInt());
            assertEquals(0, client.page("?search=%25").get("totalElements").asInt());
            assertFalse(client.page("?search=Pending").get("content").get(0).get("emailVerified").asBoolean());
            for (String invalid : new String[]{"?page=-1", "?page=1000001", "?page=abc", "?size=0", "?size=51", "?role=ROOT", "?status=enabled", "?search=" + "x".repeat(121)}) {
                assertEquals(400, client.get("/api/admin/users" + invalid).statusCode(), invalid);
            }
        }
    }

    @Test void blockingRevokesSessionsAndLoginAndUnblockingDoesNotRestoreOldCookies() throws Exception {
        var admin = account("Admin", true);
        var target = account("Learner", false);
        try (var administrator = new Client(); var learner = new Client()) {
            administrator.login(admin); learner.login(target);
            String oldCookie = learner.sessionCookie();
            assertEquals(200, learner.get("/api/auth/me").statusCode());
            assertEquals(403, administrator.put(target.getId(), "{\"enabled\":false}", false).statusCode());
            assertTrue(users.findById(target.getId()).orElseThrow().isEnabled());
            var block = administrator.put(target.getId(), "{\"enabled\":false}", true);
            assertEquals(200, block.statusCode(), block.body());
            assertFalse(json.readTree(block.body()).get("enabled").asBoolean());
            long blockedVersion = users.findById(target.getId()).orElseThrow().getSessionVersion();
            assertTrue(blockedVersion > target.getSessionVersion());
            assertEquals(401, learner.get("/api/auth/me").statusCode());
            assertEquals(401, learner.loginResponse(target).statusCode());
            assertEquals(200, administrator.put(target.getId(), "{\"enabled\":false}", true).statusCode());
            assertEquals(blockedVersion, users.findById(target.getId()).orElseThrow().getSessionVersion());
            assertEquals(200, administrator.put(target.getId(), "{\"enabled\":true}", true).statusCode());
            assertTrue(users.findById(target.getId()).orElseThrow().isEnabled());
            assertTrue(users.findById(target.getId()).orElseThrow().getSessionVersion() > blockedVersion);
            try (var oldSession = HttpClient.newHttpClient()) {
                var response = oldSession.send(HttpRequest.newBuilder(endpoint("/api/auth/me")).header("Cookie", oldCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(401, response.statusCode());
            }
            learner.login(target);
            assertEquals(200, learner.get("/api/auth/me").statusCode());
        }
    }

    @Test void protectsOwnAccountAndRejectsUserActionsMissingTargetsAndInvalidBodies() throws Exception {
        var admin = account("Admin", true);
        var learner = account("Learner", false);
        try (var client = new Client()) {
            client.login(learner);
            assertEquals(403, client.get("/api/admin/users").statusCode());
            assertEquals(403, client.put(admin.getId(), "{\"enabled\":false}", true).statusCode());
            client.login(admin);
            var self = client.put(admin.getId(), "{\"enabled\":false}", true);
            assertEquals(409, self.statusCode());
            assertEquals("You cannot block your own account.", json.readTree(self.body()).get("message").asText());
            assertTrue(users.findById(admin.getId()).orElseThrow().isEnabled());
            assertEquals(404, client.put(Long.MAX_VALUE, "{\"enabled\":false}", true).statusCode());
            for (String body : new String[]{"{}", "{\"enabled\":null}", "{\"enabled\":{}}"}) {
                assertEquals(400, client.put(learner.getId(), body, true).statusCode(), body);
            }
            assertTrue(users.findById(learner.getId()).orElseThrow().isEnabled());
        }
    }

    @Test void competingAdministratorBlocksLeaveOneActiveAdministrator() throws Exception {
        var first = account("FirstAdmin", true);
        var second = account("SecondAdmin", true);
        try (var a = new Client(); var b = new Client()) {
            a.login(first); b.login(second);
            String tokenA = a.csrf(); String tokenB = b.csrf();
            var firstRequest = CompletableFuture.supplyAsync(() -> block(a, second.getId(), tokenA));
            var secondRequest = CompletableFuture.supplyAsync(() -> block(b, first.getId(), tokenB));
            int resultA = firstRequest.join(); int resultB = secondRequest.join();
            assertTrue(resultA == 200 && (resultB == 401 || resultB == 403) || resultB == 200 && (resultA == 401 || resultA == 403), resultA + "/" + resultB);
            assertEquals(1, users.countByEnabledTrueAndEmailVerifiedAtIsNotNullAndRoles_Name(RoleName.ADMIN));
        }
    }

    private int block(Client client, Long id, String token) {
        try { return client.putWithToken(id, "{\"enabled\":false}", token).statusCode(); }
        catch (Exception exception) { throw new CompletionException(exception); }
    }
    private User account(String prefix, boolean admin) {
        String name = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(PASSWORD));
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        if (admin) user.addRole(roles.findByName(RoleName.ADMIN).orElseThrow());
        user.setEmailVerifiedAt(Instant.now()); return users.saveAndFlush(user);
    }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private class Client implements AutoCloseable {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonNode page(String query) throws Exception {
            var response = get("/api/admin/users" + query); assertEquals(200, response.statusCode(), response.body()); return json.readTree(response.body());
        }
        String csrf() throws Exception {
            var response = get("/api/auth/csrf"); assertEquals(200, response.statusCode()); return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            var response = loginResponse(user); assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> loginResponse(User user) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).timeout(Duration.ofSeconds(15)).header("X-CSRF-TOKEN", csrf()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> put(Long id, String body, boolean withToken) throws Exception { return putWithToken(id, body, withToken ? csrf() : null); }
        HttpResponse<String> putWithToken(Long id, String body, String token) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/admin/users/" + id + "/enabled")).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return http.send(request.PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        String sessionCookie() {
            return "JSESSIONID=" + cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        }
        @Override public void close() { http.close(); }
    }
}
