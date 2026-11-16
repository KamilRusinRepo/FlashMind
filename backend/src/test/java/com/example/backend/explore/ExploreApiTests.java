package com.example.backend.explore;

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
import com.example.backend.deck.*;
import com.example.backend.flashcard.*;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExploreApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "ExploreTests123!";
    @Autowired UserRepository users;
    @Autowired UserDeletionService deletion;
    @Autowired DeckRepository decks;
    @Autowired FlashcardRepository cards;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test void listsOnlyPublicDecksWithRealCountsAndPreservesDeletedAuthors() throws Exception {
        var viewer = account();
        var author = account();
        var removed = account();
        var own = deck(viewer, "Own public", true, DeckType.GENERAL, "English", null, "Tech");
        var other = deck(author, "Other public", true, DeckType.GENERAL, "Polish", null, "Science");
        var orphan = deck(removed, "Preserved public", true, DeckType.LANGUAGE, "English", "Polish", "Language");
        deck(viewer, "Own private", false, DeckType.GENERAL, "English", null, "Tech");
        deck(author, "Other private", false, DeckType.GENERAL, "English", null, "Tech");
        deck(removed, "Deleted private", false, DeckType.GENERAL, "English", null, "Tech");
        addCards(own, 2); addCards(other, 3); addCards(orphan, 1);
        deletion.deleteAccount(removed.getId());
        try (var client = new Client()) {
            assertEquals(401, client.get("").statusCode());
            client.login(viewer);
            var response = client.get("?sort=cards&size=1&ownerId=" + author.getId() + "&is_public=false");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var page = json.readTree(response.body());
            assertEquals(3, page.get("totalElements").asLong());
            assertEquals(3, page.get("totalPages").asInt());
            assertEquals(other.getId().longValue(), page.get("content").get(0).get("id").asLong());
            assertEquals(3, page.get("content").get(0).get("cardCount").asLong());
            assertEquals(author.getUsername(), page.get("content").get(0).get("author").get("displayName").asText());
            assertFalse(page.get("content").get(0).get("ownDeck").asBoolean());
            var second = client.page("?sort=cards&size=1&page=1").get("content").get(0);
            assertEquals(own.getId().longValue(), second.get("id").asLong());
            assertTrue(second.get("ownDeck").asBoolean());
            var last = client.page("?sort=cards&size=1&page=2").get("content").get(0);
            assertEquals(orphan.getId().longValue(), last.get("id").asLong());
            assertTrue(last.get("author").get("id").isNull());
            assertEquals("Deleted user", last.get("author").get("displayName").asText());
            assertFalse(response.body().contains("password"));
            assertFalse(response.body().contains(author.getEmail()));
        }
    }

    @Test void combinesFiltersSearchesAuthorsAndDescriptionsAndEscapesWildcards() throws Exception {
        var viewer = account();
        var other = account();
        var language = deck(viewer, "100% English", true, DeckType.LANGUAGE, "English", "Polish", "Language");
        language.setDescription("Beginner phrases for travel"); decks.saveAndFlush(language);
        deck(viewer, "100X English", true, DeckType.GENERAL, "English", null, "Tech");
        deck(viewer, "under_score", true, DeckType.GENERAL, "German", null, "Tech");
        deck(viewer, "underXscore", true, DeckType.GENERAL, "German", null, "Tech");
        deck(viewer, "Important!", true, DeckType.GENERAL, "Polish", null, "Personal");
        var spanish = deck(other, "Spanish words", true, DeckType.LANGUAGE, "Spanish", "English", "Language");
        deck(viewer, "Hidden phrases", false, DeckType.LANGUAGE, "English", "Polish", "Language");
        try (var client = new Client()) {
            client.login(viewer);
            assertEquals(language.getId().longValue(), client.page("?q=" + encoded("  pHrAsEs  ")).get("content").get(0).get("id").asLong());
            assertEquals(1, client.page("?q=" + encoded("%" )).get("totalElements").asLong());
            assertEquals("under_score", client.page("?q=" + encoded("_")).get("content").get(0).get("name").asText());
            assertEquals(1, client.page("?q=" + encoded("!")).get("totalElements").asLong());
            var byAuthor = client.page("?q=" + encoded(other.getUsername()));
            assertEquals(1, byAuthor.get("totalElements").asLong());
            assertEquals(spanish.getId().longValue(), byAuthor.get("content").get(0).get("id").asLong());
            var combined = client.page("?deck_type=LANGUAGE&category=language&source_language=ENGLISH&target_language=polish");
            assertEquals(1, combined.get("totalElements").asLong());
            assertEquals(language.getId().longValue(), combined.get("content").get(0).get("id").asLong());
            assertEquals(1, client.page("?deck_type=GENERAL&target_language=Polish&q=100").get("totalElements").asLong());
            assertEquals(0, client.page("?category=Science").get("totalElements").asLong());
        }
    }

    @Test void validatesQueryAndPaginationAndReturnsStableNewestPagesAndEmptyResults() throws Exception {
        var viewer = account();
        try (var client = new Client()) {
            client.login(viewer);
            var empty = client.page("");
            assertEquals(0, empty.get("totalElements").asLong());
            assertEquals(0, empty.get("totalPages").asInt());
            var first = deck(viewer, "First", true, DeckType.GENERAL, "English", null, null);
            var second = deck(viewer, "Second", true, DeckType.GENERAL, "English", null, null);
            assertEquals(second.getId().longValue(), client.page("?size=1").get("content").get(0).get("id").asLong());
            assertEquals(first.getId().longValue(), client.page("?size=1&page=1").get("content").get(0).get("id").asLong());
            var beyond = client.page("?page=99");
            assertEquals(0, beyond.get("content").size());
            assertEquals(2, beyond.get("totalElements").asLong());
            for (String query : new String[]{"?page=-1", "?page=1000001", "?page=abc", "?size=0", "?size=49", "?deck_type=INVALID",
                    "?sort=followers", "?sort=invalid", "?q=" + "x".repeat(121), "?category=" + "x".repeat(61),
                    "?source_language=" + "x".repeat(61), "?target_language=" + "x".repeat(61)}) {
                assertEquals(400, client.get(query).statusCode(), query);
            }
        }
    }

    private User account() {
        String name = "Explore" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now()); return users.saveAndFlush(user);
    }
    private Deck deck(User owner, String name, boolean publicDeck, DeckType type, String source, String target, String category) {
        var deck = new Deck(owner, name, type, source, target);
        deck.setPublic(publicDeck); deck.setCategory(category); return decks.saveAndFlush(deck);
    }
    private void addCards(Deck deck, int count) {
        for (int index = 0; index < count; index++) cards.saveAndFlush(new Flashcard(deck, "Question " + index, "Answer"));
    }
    private String encoded(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
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
        HttpResponse<String> get(String query) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint("/api/explore/decks" + query)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonNode page(String query) throws Exception {
            var response = get(query); assertEquals(200, response.statusCode(), response.body()); return json.readTree(response.body());
        }
        @Override public void close() { http.close(); }
    }
}
