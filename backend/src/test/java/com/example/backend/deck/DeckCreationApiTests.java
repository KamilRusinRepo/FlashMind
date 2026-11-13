package com.example.backend.deck;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeckCreationApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "CreateDeck123!";
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test
    void createsPrivateDeckForSessionOwnerIgnoringInjectedOwnerId() throws Exception {
        var owner = account();
        var other = account();
        try (var client = new Client()) {
            client.login(owner);
            var body = request();
            body.put("name", "  Java Basics  ");
            body.put("description", "  A first deck  ");
            body.put("category", "Tech");
            body.put("targetLanguage", "Polish");
            body.put("backgroundUrl", "https://example.invalid/cover.jpg");
            body.put("ownerId", other.getId());
            var response = client.post(body, true);
            assertEquals(201, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var data = json.readTree(response.body());
            var saved = decks.findById(data.get("id").asLong()).orElseThrow();
            assertEquals(owner.getId(), saved.getOwner().getId());
            assertEquals("Java Basics", saved.getName());
            assertEquals("A first deck", saved.getDescription());
            assertEquals("Tech", saved.getCategory());
            assertEquals("https://example.invalid/cover.jpg", saved.getBackgroundUrl());
            assertNull(saved.getTargetLanguage());
            assertFalse(saved.isPublic());
            assertNotNull(saved.getCreatedAt());
            assertFalse(data.has("owner"));
            assertEquals(1, decks.count());
        }
    }

    @Test
    void validatesLanguagesLengthsAndBackgroundUrlWithoutSavingInvalidDecks() throws Exception {
        try (var client = new Client()) {
            client.login(account());
            var language = request();
            language.put("deckType", "LANGUAGE");
            var response = client.post(language, true);
            assertEquals(400, response.statusCode());
            assertTrue(json.readTree(response.body()).get("fieldErrors").has("targetLanguage"));
            for (var invalid : List.of(Map.of("name", " "), Map.of("name", "x".repeat(121)),
                    Map.of("sourceLanguage", " "), Map.of("description", "x".repeat(2001)),
                    Map.of("category", "x".repeat(61)), Map.of("backgroundUrl", "javascript:alert(1)"),
                    Map.of("backgroundUrl", "https://user:password@example.invalid/image.jpg"))) {
                var body = request();
                body.putAll(invalid);
                var invalidResponse = client.post(body, true);
                assertEquals(400, invalidResponse.statusCode(), invalidResponse.body());
                assertTrue(json.readTree(invalidResponse.body()).get("fieldErrors").has(invalid.keySet().iterator().next()));
            }
            assertEquals(0, decks.count());
        }
    }

    @Test
    void requiresSessionAndCsrfAndCreatesPublicLanguageDeckWhenValid() throws Exception {
        try (var client = new Client()) {
            assertEquals(401, client.post(request(), true).statusCode());
            client.login(account());
            assertEquals(403, client.post(request(), false).statusCode());
            assertEquals(0, decks.count());
            var language = request();
            language.put("deckType", "LANGUAGE");
            language.put("targetLanguage", "Polish");
            language.put("publicDeck", true);
            var response = client.post(language, true);
            assertEquals(201, response.statusCode(), response.body());
            var data = json.readTree(response.body());
            assertEquals("LANGUAGE", data.get("deckType").asText());
            assertEquals("Polish", data.get("targetLanguage").asText());
            assertTrue(data.get("publicDeck").asBoolean());
        }
    }

    private Map<String, Object> request() { return new HashMap<>(Map.of("name", "My deck", "deckType", "GENERAL", "sourceLanguage", "English")); }

    private User account() {
        String name = "Create" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }

    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String csrf() throws Exception {
            var response = http.send(HttpRequest.newBuilder(endpoint("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", csrf()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            assertEquals(200, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        HttpResponse<String> post(Map<String, Object> body, boolean withCsrf) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/decks")).header("Content-Type", "application/json");
            if (withCsrf) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
