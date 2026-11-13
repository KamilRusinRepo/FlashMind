package com.example.backend.deck;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
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
class DeckManagementApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "ManageDeck123!";
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test
    void ownerCanReadUpdateAndDeleteWithoutChangingOwnerOrCreationTime() throws Exception {
        var owner = account();
        var other = account();
        var deck = deck(owner, "Original", false);
        Instant createdAt = decks.findById(deck.getId()).orElseThrow().getCreatedAt();
        Long unrelatedId = deck(other, "Unrelated", true).getId();
        try (var client = new Client()) {
            client.login(owner);
            var read = client.get(deck.getId());
            assertEquals(200, read.statusCode());
            assertEquals("Original", json.readTree(read.body()).get("name").asText());

            var body = request();
            body.put("name", "  Updated language deck  ");
            body.put("deckType", "LANGUAGE");
            body.put("targetLanguage", "Polish");
            body.put("publicDeck", true);
            body.put("description", "Updated description");
            body.put("category", "Language");
            body.put("backgroundUrl", "https://example.invalid/image.jpg");
            body.put("ownerId", other.getId());
            var response = client.mutate("PUT", deck.getId(), body, true);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var updated = decks.findById(deck.getId()).orElseThrow();
            assertEquals("Updated language deck", updated.getName());
            assertEquals("Polish", updated.getTargetLanguage());
            assertEquals("Updated description", updated.getDescription());
            assertEquals("Language", updated.getCategory());
            assertEquals("https://example.invalid/image.jpg", updated.getBackgroundUrl());
            assertTrue(updated.isPublic());
            assertEquals(owner.getId(), updated.getOwner().getId());
            assertEquals(createdAt, updated.getCreatedAt());

            var general = request();
            general.put("targetLanguage", "Polish");
            assertEquals(200, client.mutate("PUT", deck.getId(), general, true).statusCode());
            var reset = decks.findById(deck.getId()).orElseThrow();
            assertEquals(DeckType.GENERAL, reset.getDeckType());
            assertNull(reset.getTargetLanguage());
            assertNull(reset.getDescription());
            assertFalse(reset.isPublic());

            assertEquals(204, client.mutate("DELETE", deck.getId(), Map.of(), true).statusCode());
            assertEquals(404, client.get(deck.getId()).statusCode());
            assertEquals(404, client.mutate("DELETE", deck.getId(), Map.of(), true).statusCode());
            assertFalse(decks.existsById(deck.getId()));
            assertTrue(users.existsById(owner.getId()));
            assertTrue(decks.existsById(unrelatedId));
        }
    }

    @Test
    void foreignPrivatePublicOwnerlessAndMissingDecksAllReturn404() throws Exception {
        var owner = account();
        var other = account();
        var removed = account();
        Long privateId = deck(other, "Other private", false).getId();
        Long publicId = deck(other, "Other public", true).getId();
        Long orphanId = deck(removed, "Ownerless public", true).getId();
        users.delete(removed);
        users.flush();
        try (var client = new Client()) {
            client.login(owner);
            for (Long id : new Long[]{privateId, publicId, orphanId, Long.MAX_VALUE}) {
                assertEquals(404, client.get(id).statusCode());
                var attempted = request();
                attempted.put("ownerId", owner.getId());
                assertEquals(404, client.mutate("PUT", id, attempted, true).statusCode());
                assertEquals(404, client.mutate("DELETE", id, Map.of(), true).statusCode());
            }
        }
        assertEquals(3, decks.count());
        assertEquals("Other private", decks.findById(privateId).orElseThrow().getName());
        assertEquals("Other public", decks.findById(publicId).orElseThrow().getName());
        assertNull(decks.findById(orphanId).orElseThrow().getOwner());
    }

    @Test
    void sessionCsrfAndValidationAreRequiredBeforeChangesAreSaved() throws Exception {
        var owner = account();
        Long id = deck(owner, "Unchanged", false).getId();
        try (var client = new Client()) {
            assertEquals(401, client.get(id).statusCode());
            assertEquals(401, client.mutate("PUT", id, request(), true).statusCode());
            assertEquals(401, client.mutate("DELETE", id, Map.of(), true).statusCode());
            client.login(owner);
            assertEquals(403, client.mutate("PUT", id, request(), false).statusCode());
            assertEquals(403, client.mutate("DELETE", id, Map.of(), false).statusCode());
            var invalid = request();
            invalid.put("name", " ");
            var response = client.mutate("PUT", id, invalid, true);
            assertEquals(400, response.statusCode());
            assertTrue(json.readTree(response.body()).get("fieldErrors").has("name"));
            invalid = request();
            invalid.put("deckType", "LANGUAGE");
            response = client.mutate("PUT", id, invalid, true);
            assertEquals(400, response.statusCode());
            assertTrue(json.readTree(response.body()).get("fieldErrors").has("targetLanguage"));
            assertEquals("Unchanged", decks.findById(id).orElseThrow().getName());
        }
    }

    private Map<String, Object> request() { return new HashMap<>(Map.of("name", "General deck", "deckType", "GENERAL", "sourceLanguage", "English")); }
    private Deck deck(User owner, String name, boolean publicDeck) {
        var deck = new Deck(owner, name, DeckType.GENERAL, "English", null);
        deck.setPublic(publicDeck);
        return decks.saveAndFlush(deck);
    }
    private User account() {
        String name = "Manage" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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
        HttpResponse<String> get(Long id) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint("/api/decks/" + id)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> mutate(String method, Long id, Map<String, Object> body, boolean withCsrf) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/decks/" + id));
            if (withCsrf) request.header("X-CSRF-TOKEN", csrf());
            if (method.equals("PUT")) request.header("Content-Type", "application/json");
            var payload = method.equals("PUT") ? HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)) : HttpRequest.BodyPublishers.noBody();
            return http.send(request.method(method, payload).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
