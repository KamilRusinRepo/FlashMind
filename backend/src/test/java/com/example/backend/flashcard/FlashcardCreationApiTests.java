package com.example.backend.flashcard;

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

import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
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
class FlashcardCreationApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "CreateCards123!";
    @Autowired FlashcardRepository flashcards;
    @Autowired DeckRepository decks;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test
    void createsAndPersistsFlashcardWithImageAndAllowsOmittedOptionalFields() throws Exception {
        var owner = account();
        var deck = deck(owner);
        try (var client = new Client()) {
            client.login(owner);
            var request = new HashMap<String, Object>(Map.of("question", "  curiosity  ", "answer", "  ciekawość  ",
                    "exampleSentence", "  Stay curious.  ", "translatedExample", "  Bądź ciekawy.  ",
                    "imageUrl", "  https://example.invalid/image.jpg  ", "deckId", Long.MAX_VALUE, "ownerId", Long.MAX_VALUE));
            var response = client.post(deck.getId(), request, true);
            assertEquals(201, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var body = json.readTree(response.body());
            var saved = flashcards.findById(body.get("id").asLong()).orElseThrow();
            assertEquals(deck.getId(), saved.getDeck().getId());
            assertEquals("curiosity", saved.getQuestion());
            assertEquals("ciekawość", saved.getAnswer());
            assertEquals("Stay curious.", saved.getExampleSentence());
            assertEquals("Bądź ciekawy.", saved.getTranslatedExample());
            assertEquals("https://example.invalid/image.jpg", saved.getImageUrl());
            assertEquals(saved.getImageUrl(), body.get("imageUrl").asText());
            assertTrue(body.hasNonNull("createdAt"));
            assertFalse(body.has("deck"));
            assertFalse(body.has("owner"));

            var minimal = client.post(deck.getId(), Map.of("question", "Question", "answer", "Answer"), true);
            assertEquals(201, minimal.statusCode(), minimal.body());
            var minimalBody = json.readTree(minimal.body());
            assertTrue(minimalBody.get("imageUrl").isNull());
            assertTrue(minimalBody.get("exampleSentence").isNull());
            assertTrue(minimalBody.get("translatedExample").isNull());
        }
    }

    @Test
    void returnsFieldErrorsWithoutSavingInvalidContentOrImageUrls() throws Exception {
        var owner = account();
        var deck = deck(owner);
        long before = flashcards.count();
        try (var client = new Client()) {
            client.login(owner);
            var invalid = Map.of("question", " ", "answer", "\n", "exampleSentence", "x".repeat(1001),
                    "translatedExample", "x".repeat(1001), "imageUrl", "javascript:alert(1)");
            var response = client.post(deck.getId(), invalid, true);
            assertEquals(400, response.statusCode(), response.body());
            var errors = json.readTree(response.body()).get("fieldErrors");
            for (String field : invalid.keySet()) assertTrue(errors.has(field), field);
            for (String url : new String[]{"/relative.jpg", "ftp://example.invalid/image.jpg", "https://user:password@example.invalid/image.jpg", "https://"}) {
                var result = client.post(deck.getId(), Map.of("question", "Question", "answer", "Answer", "imageUrl", url), true);
                assertEquals(400, result.statusCode(), result.body());
                assertTrue(json.readTree(result.body()).get("fieldErrors").has("imageUrl"));
            }
            assertEquals(before, flashcards.count());
        }
    }

    @Test
    void requiresSessionCsrfAndOwnershipIncludingPublicAndOwnerlessDecks() throws Exception {
        var owner = account();
        var own = deck(owner);
        var foreign = deck(account());
        var foreignPublic = deck(account());
        foreignPublic.setPublic(true);
        decks.saveAndFlush(foreignPublic);
        var removed = account();
        var orphan = deck(removed);
        orphan.setPublic(true);
        decks.saveAndFlush(orphan);
        users.delete(removed);
        users.flush();
        var body = Map.of("question", "Question", "answer", "Answer");
        long before = flashcards.count();
        try (var client = new Client()) {
            assertEquals(401, client.post(own.getId(), body, true).statusCode());
            client.login(owner);
            assertEquals(403, client.post(own.getId(), body, false).statusCode());
            for (Long id : new Long[]{foreign.getId(), foreignPublic.getId(), orphan.getId(), Long.MAX_VALUE}) {
                var response = client.post(id, body, true);
                assertEquals(404, response.statusCode(), response.body());
            }
            assertEquals(before, flashcards.count());
        }
    }

    private User account() {
        String name = "Create" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }
    private Deck deck(User owner) { return decks.saveAndFlush(new Deck(owner, "Deck", DeckType.GENERAL, "English", null)); }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }

    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        private String csrf() throws Exception {
            var response = http.send(HttpRequest.newBuilder(endpoint("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", csrf())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> post(Long deckId, Map<String, ?> body, boolean protectedRequest) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/decks/" + deckId + "/flashcards")).header("Content-Type", "application/json");
            if (protectedRequest) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
