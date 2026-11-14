package com.example.backend.flashcard;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
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
class FlashcardManagementApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "ManageCards123!";
    @Autowired FlashcardRepository flashcards;
    @Autowired DeckRepository decks;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test
    void editsContentAndImageThenClearsOptionalFieldsWithoutMovingCard() throws Exception {
        var owner = account();
        var deck = deck(owner);
        var card = card(deck);
        var originalTime = flashcards.findById(card.getId()).orElseThrow().getCreatedAt();
        try (var client = new Client()) {
            client.login(owner);
            var response = client.send("PUT", deck.getId(), card.getId(), Map.of("question", "  New question  ", "answer", "  New answer  ",
                    "exampleSentence", "  Example  ", "translatedExample", "  Translation  ", "imageUrl", "https://example.invalid/new.jpg",
                    "deckId", Long.MAX_VALUE, "ownerId", Long.MAX_VALUE), true);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var saved = flashcards.findById(card.getId()).orElseThrow();
            assertEquals("New question", saved.getQuestion());
            assertEquals("New answer", saved.getAnswer());
            assertEquals("Example", saved.getExampleSentence());
            assertEquals("Translation", saved.getTranslatedExample());
            assertEquals("https://example.invalid/new.jpg", saved.getImageUrl());
            assertEquals(deck.getId(), saved.getDeck().getId());
            assertEquals(originalTime, saved.getCreatedAt());
            assertEquals(card.getId().longValue(), json.readTree(response.body()).get("id").asLong());

            var cleared = client.send("PUT", deck.getId(), card.getId(), Map.of("question", "Question", "answer", "Answer", "imageUrl", " "), true);
            assertEquals(200, cleared.statusCode(), cleared.body());
            var withoutImage = flashcards.findById(card.getId()).orElseThrow();
            assertNull(withoutImage.getImageUrl());
            assertNull(withoutImage.getExampleSentence());
            assertNull(withoutImage.getTranslatedExample());

            var invalid = client.send("PUT", deck.getId(), card.getId(), Map.of("question", " ", "answer", "Invalid", "imageUrl", "javascript:alert(1)"), true);
            assertEquals(400, invalid.statusCode(), invalid.body());
            var errors = json.readTree(invalid.body()).get("fieldErrors");
            assertTrue(errors.has("question"));
            assertTrue(errors.has("imageUrl"));
            assertEquals("Question", flashcards.findById(card.getId()).orElseThrow().getQuestion());
        }
    }

    @Test
    void deletesOnlySelectedFlashcardAndRepeatedDeleteReturns404() throws Exception {
        var owner = account();
        var deck = deck(owner);
        var card = card(deck);
        var retained = card(deck);
        try (var client = new Client()) {
            client.login(owner);
            var response = client.send("DELETE", deck.getId(), card.getId(), Map.of(), true);
            assertEquals(204, response.statusCode(), response.body());
            assertTrue(response.body().isEmpty());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(flashcards.existsById(card.getId()));
            assertTrue(flashcards.existsById(retained.getId()));
            assertTrue(decks.existsById(deck.getId()));
            assertTrue(users.existsById(owner.getId()));
            assertEquals(404, client.send("DELETE", deck.getId(), card.getId(), Map.of(), true).statusCode());
        }
    }

    @Test
    void mutationsRequireSessionCsrfAndMatchingOwnedDeckAndCard() throws Exception {
        var owner = account();
        var own = deck(owner);
        var ownCard = card(own);
        var otherOwnedDeck = deck(owner);
        var otherOwnedCard = card(otherOwnedDeck);
        var foreign = deck(account());
        var foreignCard = card(foreign);
        var foreignPublic = deck(account());
        foreignPublic.setPublic(true);
        decks.saveAndFlush(foreignPublic);
        var publicCard = card(foreignPublic);
        var removed = account();
        var orphan = deck(removed);
        orphan.setPublic(true);
        decks.saveAndFlush(orphan);
        var orphanCard = card(orphan);
        users.delete(removed);
        users.flush();
        long before = flashcards.count();
        var body = Map.of("question", "Changed", "answer", "Changed");

        for (String method : new String[]{"PUT", "DELETE"}) {
            try (var client = new Client()) {
                assertEquals(401, client.send(method, own.getId(), ownCard.getId(), body, true).statusCode());
                client.login(owner);
                assertEquals(403, client.send(method, own.getId(), ownCard.getId(), body, false).statusCode());
                for (Long[] ids : new Long[][]{{own.getId(), otherOwnedCard.getId()}, {own.getId(), foreignCard.getId()},
                        {foreign.getId(), foreignCard.getId()}, {foreignPublic.getId(), publicCard.getId()}, {orphan.getId(), orphanCard.getId()},
                        {own.getId(), Long.MAX_VALUE}, {Long.MAX_VALUE, ownCard.getId()}}) {
                    var response = client.send(method, ids[0], ids[1], body, true);
                    assertEquals(404, response.statusCode(), method + ": " + response.body());
                }
            }
        }
        assertEquals(before, flashcards.count());
        assertEquals("Question", flashcards.findById(ownCard.getId()).orElseThrow().getQuestion());
        assertEquals("Question", flashcards.findById(foreignCard.getId()).orElseThrow().getQuestion());
    }

    private User account() {
        String name = "Manage" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }
    private Deck deck(User owner) { return decks.saveAndFlush(new Deck(owner, "Deck", DeckType.GENERAL, "English", null)); }
    private Flashcard card(Deck deck) { return flashcards.saveAndFlush(new Flashcard(deck, "Question", "Answer")); }
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
                    .header("X-CSRF-TOKEN", csrf()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> send(String method, Long deckId, Long cardId, Map<String, ?> body, boolean protectedRequest) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/decks/" + deckId + "/flashcards/" + cardId)).header("Content-Type", "application/json");
            if (protectedRequest) request.header("X-CSRF-TOKEN", csrf());
            var publisher = method.equals("DELETE") ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
            return http.send(request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
