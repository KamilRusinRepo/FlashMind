package com.example.backend.flashcard.csv;

import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
import com.example.backend.deck.follow.DeckFollowService;
import com.example.backend.flashcard.Flashcard;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.User;
import com.example.backend.user.UserDeletionService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlashcardCsvApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "CsvExportTests123!";
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DeckRepository decks;
    @Autowired FlashcardRepository cards;
    @Autowired DeckFollowService follows;
    @Autowired UserDeletionService deletion;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test void exportsAllCardsRegardlessOfPaginationAndSupportsEmptyDecks() throws Exception {
        var owner = account(false);
        var deck = decks.saveAndFlush(new Deck(owner, "My cards", DeckType.GENERAL, "English", null));
        var first = new Flashcard(deck, "Hello, \"world\"", "Cześć, świecie");
        first.setExampleSentence("One\nTwo"); first.setTranslatedExample("Jeden\nDwa");
        first.setImageUrl("https://example.invalid/image.png"); cards.saveAndFlush(first);
        var expected = new StringBuilder("question,answer,example_sentence,translated_example,image_url\r\n")
                .append("\"Hello, \"\"world\"\"\",\"Cześć, świecie\",\"One\nTwo\",\"Jeden\nDwa\",\"https://example.invalid/image.png\"\r\n");
        for (int index = 0; index < 15; index++) {
            cards.saveAndFlush(new Flashcard(deck, "Question " + index, "Answer " + index));
            expected.append("\"Question ").append(index).append("\",\"Answer ").append(index).append("\",\"\",\"\",\"\"\r\n");
        }
        var empty = decks.saveAndFlush(new Deck(owner, "Empty", DeckType.GENERAL, "English", null));
        try (var client = new Client()) {
            client.login(owner);
            var response = client.export(deck.getId());
            assertEquals(200, response.statusCode(), response.body());
            assertEquals(expected.toString(), response.body());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().toLowerCase().contains("text/csv;charset=utf-8"));
            assertEquals("attachment; filename=\"deck-" + deck.getId() + "-flashcards.csv\"", response.headers().firstValue("Content-Disposition").orElseThrow());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(16, cards.findByDeck_IdAndDeck_Owner_IdOrderByCreatedAtAscIdAsc(deck.getId(), owner.getId()).size());
            assertEquals("question,answer,example_sentence,translated_example,image_url\r\n", client.export(empty.getId()).body());
        }
    }

    @Test void exportRequiresOwnershipEvenForFollowedPublicAuthorlessAndEmptyDecks() throws Exception {
        var owner = account(false); var viewer = account(false); var admin = account(true); var removed = account(false);
        var shared = new Deck(owner, "Public", DeckType.GENERAL, "English", null);
        shared.setPublic(true); shared = decks.saveAndFlush(shared);
        cards.saveAndFlush(new Flashcard(shared, "Private card text", "Answer"));
        var empty = decks.saveAndFlush(new Deck(owner, "Empty private", DeckType.GENERAL, "English", null));
        var orphan = new Deck(removed, "Orphan", DeckType.GENERAL, "English", null);
        orphan.setPublic(true); orphan = decks.saveAndFlush(orphan); deletion.deleteAccount(removed.getId());
        follows.follow(viewer.getId(), shared.getId());
        try (var client = new Client()) {
            assertEquals(401, client.export(shared.getId()).statusCode());
            client.login(viewer);
            var rejected = client.export(shared.getId());
            assertEquals(404, rejected.statusCode(), rejected.body());
            assertFalse(rejected.body().contains("Private card text"));
            assertEquals(404, client.export(empty.getId()).statusCode());
            assertEquals(404, client.export(orphan.getId()).statusCode());
            assertEquals(404, client.export(Long.MAX_VALUE).statusCode());
            assertEquals(400, client.export(0L).statusCode());
            client.login(admin); assertEquals(404, client.export(shared.getId()).statusCode());
            client.login(owner); assertEquals(200, client.export(shared.getId()).statusCode());
        }
    }

    private User account(boolean admin) {
        String name = "CsvExport" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        if (admin) user.addRole(roles.findByName(RoleName.ADMIN).orElseThrow());
        user.setEmailVerifiedAt(Instant.now()); return users.saveAndFlush(user);
    }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).timeout(Duration.ofSeconds(15)).header("Accept", "text/csv, application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> export(Long id) throws Exception { return get("/api/decks/" + id + "/flashcards/export"); }
        void login(User user) throws Exception {
            String csrf = json.readTree(get("/api/auth/csrf").body()).get("token").asText();
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json").header("X-CSRF-TOKEN", csrf)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString()); assertEquals(200, response.statusCode(), response.body());
        }
        @Override public void close() { http.close(); }
    }
}
