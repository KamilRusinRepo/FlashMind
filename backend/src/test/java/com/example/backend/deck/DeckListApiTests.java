package com.example.backend.deck;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeckListApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "ListDecks123!";
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test
    void pagesOnlySessionOwnersDecksAndExcludesOtherAndOwnerlessPublicDecks() throws Exception {
        var owner = account();
        var other = account();
        var removed = account();
        Long firstId = deck(owner, "First").getId();
        Long secondId = deck(owner, "Second").getId();
        deck(other, "Other person's private deck");
        var orphan = deck(removed, "Public deck without owner");
        orphan.setPublic(true);
        decks.saveAndFlush(orphan);
        users.delete(removed);
        users.flush();
        try (var client = new Client()) {
            client.login(owner);
            var response = client.get("?size=1&ownerId=" + other.getId());
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var page = json.readTree(response.body());
            assertEquals(2, page.get("totalElements").asLong());
            assertEquals(2, page.get("totalPages").asInt());
            assertEquals(0, page.get("page").asInt());
            assertEquals(secondId.longValue(), page.get("content").get(0).get("id").asLong());
            assertFalse(page.get("content").get(0).has("owner"));
            var next = json.readTree(client.get("?page=1&size=1").body());
            assertEquals(firstId.longValue(), next.get("content").get(0).get("id").asLong());
            var beyond = json.readTree(client.get("?page=2&size=1").body());
            assertEquals(0, beyond.get("content").size());
            assertEquals(2, beyond.get("totalElements").asLong());
        }
    }

    @Test
    void searchIsCaseInsensitiveTrimmedAndTreatsWildcardCharactersLiterally() throws Exception {
        var owner = account();
        deck(owner, "Java Basics");
        deck(owner, "100% Java");
        deck(owner, "100X Java");
        deck(owner, "under_score");
        deck(owner, "underXscore");
        deck(owner, "Important!");
        deck(account(), "Java from another owner");
        try (var client = new Client()) {
            client.login(owner);
            assertEquals(3, client.search("  jAvA  ").get("totalElements").asLong());
            assertEquals("100% Java", client.search("%").get("content").get(0).get("name").asText());
            assertEquals(1, client.search("_").get("totalElements").asLong());
            assertEquals(1, client.search("!").get("totalElements").asLong());
            assertEquals(0, client.search("does not exist").get("totalElements").asLong());
        }
    }

    @Test
    void requiresAuthenticationAndRejectsInvalidPaginationAndSearchLength() throws Exception {
        try (var client = new Client()) {
            assertEquals(401, client.get("").statusCode());
            client.login(account());
            for (String query : new String[]{"?page=-1", "?page=1000001", "?size=0", "?size=49", "?page=abc", "?search=" + "x".repeat(121)}) {
                assertEquals(400, client.get(query).statusCode(), query);
            }
            var empty = json.readTree(client.get("").body());
            assertEquals(0, empty.get("totalElements").asLong());
            assertEquals(0, empty.get("totalPages").asInt());
        }
    }

    private User account() {
        String name = "List" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private Deck deck(User owner, String name) {
        return decks.saveAndFlush(new Deck(owner, name, DeckType.GENERAL, "English", null));
    }

    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }

    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        void login(User user) throws Exception {
            var csrf = http.send(HttpRequest.newBuilder(endpoint("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, csrf.statusCode());
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", json.readTree(csrf.body()).get("token").asText())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            assertEquals(200, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        HttpResponse<String> get(String query) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint("/api/decks" + query)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonNode search(String value) throws Exception {
            var response = get("?search=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode(), response.body());
            return json.readTree(response.body());
        }
        @Override public void close() { http.close(); }
    }
}
