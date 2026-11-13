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
class FlashcardListApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "FlashcardList123!";
    @Autowired FlashcardRepository flashcards;
    @Autowired DeckRepository decks;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test
    void paginatesOnlyRequestedOwnedDeckAndReturnsDtoWithImage() throws Exception {
        var owner = account();
        var deck = deck(owner);
        Long firstId = card(deck, "First").getId();
        var second = card(deck, "Second");
        second.setImageUrl("https://example.invalid/image.jpg");
        second.setExampleSentence("Example sentence");
        second.setTranslatedExample("Translated example");
        flashcards.saveAndFlush(second);
        card(deck(owner), "Another deck of same owner");
        card(deck(account()), "Another owner");

        try (var client = new Client()) {
            client.login(owner);
            var response = client.get(deck.getId(), "?size=1");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var page = json.readTree(response.body());
            assertEquals(2, page.get("totalElements").asLong());
            assertEquals(2, page.get("totalPages").asInt());
            assertEquals(0, page.get("page").asInt());
            assertEquals(1, page.get("size").asInt());
            var result = page.get("content").get(0);
            assertEquals(second.getId().longValue(), result.get("id").asLong());
            assertEquals("Second", result.get("question").asText());
            assertEquals("Answer", result.get("answer").asText());
            assertEquals("https://example.invalid/image.jpg", result.get("imageUrl").asText());
            assertEquals("Example sentence", result.get("exampleSentence").asText());
            assertEquals("Translated example", result.get("translatedExample").asText());
            assertTrue(result.hasNonNull("createdAt"));
            assertFalse(result.has("deck"));
            assertFalse(result.has("owner"));
            var next = json.readTree(client.get(deck.getId(), "?page=1&size=1").body());
            assertEquals(firstId.longValue(), next.get("content").get(0).get("id").asLong());
            assertTrue(next.get("content").get(0).get("imageUrl").isNull());
            var beyond = json.readTree(client.get(deck.getId(), "?page=2&size=1").body());
            assertEquals(0, beyond.get("content").size());
            assertEquals(2, beyond.get("totalElements").asLong());
        }
    }

    @Test
    void returns404ForForeignPrivatePublicEmptyOwnerlessAndMissingDecks() throws Exception {
        var owner = account();
        var other = account();
        var privateDeck = deck(other);
        card(privateDeck, "Private");
        var publicDeck = deck(other);
        publicDeck.setPublic(true);
        decks.saveAndFlush(publicDeck);
        card(publicDeck, "Public");
        var emptyDeck = deck(other);
        var removed = account();
        var orphan = deck(removed);
        orphan.setPublic(true);
        decks.saveAndFlush(orphan);
        card(orphan, "Ownerless");
        users.delete(removed);
        users.flush();

        try (var client = new Client()) {
            client.login(owner);
            for (Long id : new Long[]{privateDeck.getId(), publicDeck.getId(), emptyDeck.getId(), orphan.getId(), Long.MAX_VALUE}) {
                var response = client.get(id, "?ownerId=" + other.getId());
                assertEquals(404, response.statusCode(), response.body());
                var error = json.readTree(response.body());
                assertEquals(404, error.get("status").asInt());
                assertFalse(error.has("content"));
            }
        }
    }

    @Test
    void requiresSessionValidatesPaginationAndReturnsEmptyOwnedDeck() throws Exception {
        var owner = account();
        var deck = deck(owner);
        try (var client = new Client()) {
            assertEquals(401, client.get(deck.getId(), "").statusCode());
            client.login(owner);
            for (String query : new String[]{"?page=-1", "?page=1000001", "?size=0", "?size=49", "?page=abc"}) {
                assertEquals(400, client.get(deck.getId(), query).statusCode(), query);
            }
            var response = client.get(deck.getId(), "");
            assertEquals(200, response.statusCode(), response.body());
            var page = json.readTree(response.body());
            assertEquals(0, page.get("content").size());
            assertEquals(0, page.get("totalElements").asLong());
            assertEquals(0, page.get("totalPages").asInt());
        }
    }

    private User account() {
        String name = "Cards" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private Deck deck(User owner) { return decks.saveAndFlush(new Deck(owner, "Deck", DeckType.GENERAL, "English", null)); }
    private Flashcard card(Deck deck, String question) { return flashcards.saveAndFlush(new Flashcard(deck, question, "Answer")); }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }

    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        void login(User user) throws Exception {
            var csrf = http.send(HttpRequest.newBuilder(endpoint("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, csrf.statusCode());
            var token = json.readTree(csrf.body());
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).header("Content-Type", "application/json")
                    .header(token.get("headerName").asText(), token.get("token").asText())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            assertEquals(200, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        HttpResponse<String> get(Long deckId, String query) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint("/api/decks/" + deckId + "/flashcards" + query)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
