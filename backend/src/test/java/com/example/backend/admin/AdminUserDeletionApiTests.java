package com.example.backend.admin;

import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.InvalidTokenException;
import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
import com.example.backend.flashcard.Flashcard;
import com.example.backend.flashcard.FlashcardRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserDeletionApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "AdminDeletionTests123!";
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DeckRepository decks;
    @Autowired FlashcardRepository cards;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;

    @Test void deletesAccountAndPrivateDeckButRetainsPublicContentAndOtherSessions() throws Exception {
        var admin = account(true); var target = account(false);
        var deck = new Deck(target, "Retained public", DeckType.GENERAL, "English", null);
        deck.setPublic(true); deck = decks.saveAndFlush(deck);
        Long publicId = deck.getId();
        Long cardId = cards.saveAndFlush(new Flashcard(deck, "Question", "Answer")).getId();
        Long privateId = decks.saveAndFlush(new Deck(target, "Private", DeckType.GENERAL, "English", null)).getId();
        jdbc.update("INSERT INTO deck_follows(user_id, deck_id) VALUES (?, ?)", admin.getId(), publicId);
        var reset = tokens.issue(target.getId(), TokenType.PASSWORD_RESET);
        try (var a = new Client(); var learner = new Client()) {
            a.login(admin); learner.login(target);
            var response = a.delete("/api/admin/users/" + target.getId(), true);
            assertEquals(204, response.statusCode(), response.body());
            assertEquals("", response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(users.existsById(target.getId()));
            assertFalse(decks.existsById(privateId));
            assertTrue(cards.existsById(cardId));
            assertEquals(401, learner.get("/api/auth/me").statusCode());
            assertEquals(200, a.get("/api/auth/me").statusCode());
            var details = a.get("/api/explore/decks/" + publicId);
            assertEquals(200, details.statusCode(), details.body());
            var data = json.readTree(details.body());
            assertEquals("Deleted user", data.get("author").get("displayName").asText());
            assertTrue(data.get("author").get("id").isNull());
            assertEquals(1, data.get("cardCount").asInt());
            assertEquals(1, data.get("followerCount").asInt());
            assertTrue(data.get("following").asBoolean());
            var listed = a.get("/api/explore/decks?q=Retained");
            assertEquals(200, listed.statusCode(), listed.body());
            assertEquals(publicId.longValue(), json.readTree(listed.body()).get("content").get(0).get("id").asLong());
            assertEquals(404, a.delete("/api/admin/users/" + target.getId(), true).statusCode());
        }
        assertThrows(InvalidTokenException.class, () -> tokens.consume(reset.value(), TokenType.PASSWORD_RESET));
        assertTrue(roles.findByName(RoleName.USER).isPresent());
        assertTrue(roles.findByName(RoleName.ADMIN).isPresent());
    }

    @Test void requiresAdminAndCsrfAndProtectsLastAdminThroughBothDeletionRoutes() throws Exception {
        var admin = account(true); var target = account(false);
        try (var a = new Client(); var learner = new Client(); var anonymous = new Client()) {
            String path = "/api/admin/users/" + target.getId();
            assertEquals(401, anonymous.delete(path, true).statusCode());
            learner.login(target); assertEquals(403, learner.delete(path, true).statusCode());
            a.login(admin); assertEquals(403, a.delete(path, false).statusCode());
            var rejected = a.delete("/api/admin/users/" + admin.getId(), true);
            assertEquals(409, rejected.statusCode());
            assertEquals("You cannot delete the last active administrator.", json.readTree(rejected.body()).get("message").asText());
            assertEquals(409, a.delete("/api/users/me", true).statusCode());
            assertEquals(200, a.get("/api/auth/me").statusCode());
            assertEquals(400, a.delete("/api/admin/users/0", true).statusCode());
            assertEquals(404, a.delete("/api/admin/users/" + Long.MAX_VALUE, true).statusCode());
            assertTrue(users.existsById(admin.getId())); assertTrue(users.existsById(target.getId()));
        }
    }

    @Test void allowsSelfDeletionWithAnotherActiveAdminAndRejectsOtherSessions() throws Exception {
        var admin = account(true); var remaining = account(true);
        try (var a = new Client(); var sameAccount = new Client(); var other = new Client()) {
            a.login(admin); sameAccount.login(admin); other.login(remaining);
            assertEquals(204, a.delete("/api/admin/users/" + admin.getId(), true).statusCode());
            assertEquals(401, a.get("/api/auth/me").statusCode());
            assertEquals(401, sameAccount.get("/api/admin/access").statusCode());
            assertEquals(204, other.get("/api/admin/access").statusCode());
            assertEquals(1, users.countByEnabledTrueAndEmailVerifiedAtIsNotNullAndRoles_Name(RoleName.ADMIN));
        }
    }

    @Test void concurrentDeletesCannotRemoveBothAdministrators() throws Exception {
        var first = account(true); var second = account(true);
        try (var a = new Client(); var b = new Client()) {
            a.login(first); b.login(second);
            String tokenA = a.csrf(); String tokenB = b.csrf();
            var one = CompletableFuture.supplyAsync(() -> deleteStatus(a, second.getId(), tokenA));
            var two = CompletableFuture.supplyAsync(() -> deleteStatus(b, first.getId(), tokenB));
            int statusA = one.join(); int statusB = two.join();
            assertTrue(statusA == 204 && statusB == 401 || statusB == 204 && statusA == 401,
                    "Unexpected statuses: " + statusA + ", " + statusB);
            assertEquals(1, users.countByEnabledTrueAndEmailVerifiedAtIsNotNullAndRoles_Name(RoleName.ADMIN));
        }
    }

    private int deleteStatus(Client client, Long id, String token) {
        try { return client.deleteWithToken("/api/admin/users/" + id, token).statusCode(); }
        catch (Exception exception) { throw new CompletionException(exception); }
    }
    private User account(boolean admin) {
        String name = "AdminDelete" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json").header("X-CSRF-TOKEN", csrf())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD))));
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString()); assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> delete(String path, boolean withToken) throws Exception { return deleteWithToken(path, withToken ? csrf() : null); }
        HttpResponse<String> deleteWithToken(String path, String token) throws Exception {
            var request = HttpRequest.newBuilder(endpoint(path)).timeout(Duration.ofSeconds(15));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return http.send(request.DELETE().build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
