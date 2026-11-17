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
import com.example.backend.deck.follow.DeckFollowService;
import com.example.backend.study.*;
import com.example.backend.study.dto.request.SubmitStudyAnswerRequest;
import org.springframework.web.server.ResponseStatusException;
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
    @Autowired DeckFollowService follows;
    @Autowired StudyService study;
    @Autowired FlashcardProgressRepository progress;

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
                    "?sort=rating", "?sort=invalid", "?q=" + "x".repeat(121), "?category=" + "x".repeat(61),
                    "?source_language=" + "x".repeat(61), "?target_language=" + "x".repeat(61)}) {
                assertEquals(400, client.get(query).statusCode(), query);
            }
        }
    }

    @Test void publicDetailsAndCardsRejectPrivateMissingAndAnonymousAccess() throws Exception {
        var viewer = account();
        var author = account();
        var publicDeck = deck(author, "Public details", true, DeckType.LANGUAGE, "English", "Polish", "Language");
        var privateDeck = deck(author, "Private details", false, DeckType.GENERAL, "English", null, null);
        var ownPrivate = deck(viewer, "Own private", false, DeckType.GENERAL, "English", null, null);
        var card = new Flashcard(publicDeck, "Question", "Answer");
        card.setExampleSentence("Example"); card.setTranslatedExample("Translation");
        card.setImageUrl("https://example.com/card.jpg"); cards.saveAndFlush(card);
        addCards(privateDeck, 1);
        try (var client = new Client()) {
            assertEquals(401, client.get("/" + publicDeck.getId()).statusCode());
            assertEquals(401, client.get("/" + publicDeck.getId() + "/flashcards").statusCode());
            client.login(viewer);
            var response = client.get("/" + publicDeck.getId());
            assertEquals(200, response.statusCode());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var detail = json.readTree(response.body());
            assertEquals(author.getUsername(), detail.get("author").get("displayName").asText());
            assertEquals(1, detail.get("cardCount").asInt());
            assertFalse(detail.get("ownDeck").asBoolean());
            assertFalse(response.body().contains(author.getEmail()));
            var list = client.page("/" + publicDeck.getId() + "/flashcards");
            assertEquals(1, list.get("totalElements").asInt());
            var saved = list.get("content").get(0);
            assertEquals("Question", saved.get("question").asText());
            assertEquals("Answer", saved.get("answer").asText());
            assertEquals("Example", saved.get("exampleSentence").asText());
            assertEquals("Translation", saved.get("translatedExample").asText());
            assertEquals("https://example.com/card.jpg", saved.get("imageUrl").asText());
            for (Long id : new Long[]{privateDeck.getId(), ownPrivate.getId(), Long.MAX_VALUE}) {
                assertEquals(404, client.get("/" + id).statusCode());
                assertEquals(404, client.get("/" + id + "/flashcards").statusCode());
            }
            publicDeck.setPublic(false); decks.saveAndFlush(publicDeck);
            assertEquals(404, client.get("/" + publicDeck.getId()).statusCode());
            assertEquals(404, client.get("/" + publicDeck.getId() + "/flashcards").statusCode());
        }
    }

    @Test void publicCardPagesPreserveDeletedAuthorAndReturnStableOrderAndEmptyDecks() throws Exception {
        var viewer = account();
        var author = account();
        var publicDeck = deck(author, "Preserved", true, DeckType.GENERAL, "English", null, null);
        addCards(publicDeck, 13);
        deletion.deleteAccount(author.getId());
        var empty = deck(viewer, "Empty", true, DeckType.GENERAL, "Polish", null, null);
        try (var client = new Client()) {
            client.login(viewer);
            var detail = client.page("/" + publicDeck.getId());
            assertTrue(detail.get("author").get("id").isNull());
            assertEquals("Deleted user", detail.get("author").get("displayName").asText());
            var first = client.page("/" + publicDeck.getId() + "/flashcards");
            assertEquals(12, first.get("content").size());
            assertEquals(13, first.get("totalElements").asInt());
            assertEquals(2, first.get("totalPages").asInt());
            assertEquals("Question 0", first.get("content").get(0).get("question").asText());
            var last = client.page("/" + publicDeck.getId() + "/flashcards?page=1");
            assertEquals(1, last.get("content").size());
            assertEquals("Question 12", last.get("content").get(0).get("question").asText());
            assertEquals(0, client.page("/" + publicDeck.getId() + "/flashcards?page=2").get("content").size());
            var emptyCards = client.page("/" + empty.getId() + "/flashcards");
            assertEquals(0, emptyCards.get("totalPages").asInt());
            assertTrue(client.page("/" + empty.getId()).get("ownDeck").asBoolean());
            for (String query : new String[]{"?page=-1", "?page=1000001", "?size=0", "?size=49", "?page=abc"}) {
                assertEquals(400, client.get("/" + publicDeck.getId() + "/flashcards" + query).statusCode());
            }
        }
    }

    @Test void followsIdempotentlyCountsFollowersAndListsLibraryWithoutGrantingEditAccess() throws Exception {
        var viewer = account();
        var other = account();
        var author = account();
        var shared = deck(author, "Shared library", true, DeckType.GENERAL, "English", null, "Tech");
        var own = deck(viewer, "Own library", false, DeckType.GENERAL, "English", null, null);
        var hidden = deck(author, "Hidden library", false, DeckType.GENERAL, "English", null, null);
        String path = "/api/explore/decks/" + shared.getId() + "/follow";
        try (var client = new Client()) {
            client.login(viewer);
            assertEquals(403, client.send("PUT", path, "", false).statusCode());
            assertEquals(403, client.send("DELETE", path, "", false).statusCode());
            for (int retry = 0; retry < 2; retry++) {
                var response = client.send("PUT", path, "", true);
                assertEquals(200, response.statusCode(), response.body());
                var data = json.readTree(response.body());
                assertTrue(data.get("following").asBoolean());
                assertEquals(1, data.get("followerCount").asInt());
            }
            follows.follow(other.getId(), shared.getId());
            assertEquals(2, client.page("/" + shared.getId()).get("followerCount").asInt());
            var library = json.readTree(client.getPath("/api/decks/library?size=1&search=library").body());
            assertEquals(2, library.get("totalElements").asInt());
            assertEquals(2, library.get("totalPages").asInt());
            var second = json.readTree(client.getPath("/api/decks/library?size=1&page=1").body());
            assertNotEquals(library.get("content").get(0).get("id").asLong(), second.get("content").get(0).get("id").asLong());
            var followed = json.readTree(client.getPath("/api/decks/library?search=Shared").body()).get("content").get(0);
            assertFalse(followed.get("ownDeck").asBoolean());
            assertEquals(author.getUsername(), followed.get("author").get("displayName").asText());
            assertEquals(404, client.send("DELETE", "/api/decks/" + shared.getId(), "", true).statusCode());
            assertEquals(404, client.send("PUT", "/api/decks/" + shared.getId(), json.writeValueAsString(Map.of(
                    "name", "Forged", "deckType", "GENERAL", "sourceLanguage", "English", "publicDeck", true)), true).statusCode());
            assertEquals(409, client.send("PUT", "/api/explore/decks/" + own.getId() + "/follow", "", true).statusCode());
            assertEquals(404, client.send("PUT", "/api/explore/decks/" + hidden.getId() + "/follow", "", true).statusCode());
            for (int retry = 0; retry < 2; retry++) assertEquals(204, client.send("DELETE", path, "", true).statusCode());
            assertEquals(1, client.page("/" + shared.getId()).get("followerCount").asInt());
            assertFalse(client.page("/" + shared.getId()).get("following").asBoolean());
            assertEquals(1, json.readTree(client.getPath("/api/decks/library").body()).get("totalElements").asInt());
            assertEquals(shared.getId().longValue(), client.page("?sort=followers").get("content").get(0).get("id").asLong());
        }
    }

    @Test void studiesFollowedDeckWithSeparateProgressAndRevokesAllSessionActionsOnUnfollow() throws Exception {
        var viewer = account();
        var author = account();
        var shared = deck(author, "Study shared", true, DeckType.GENERAL, "English", null, null);
        var card = cards.saveAndFlush(new Flashcard(shared, "Question", "Answer"));
        var ownerProgress = new FlashcardProgress(author, card);
        ownerProgress.recordReview(true, 4, 0, Instant.now(), Instant.now().plusSeconds(864000));
        progress.saveAndFlush(ownerProgress);
        assertNotFound(() -> study.start(viewer.getId(), shared.getId(), StudyMode.SCHEDULED_REVIEW));
        follows.follow(viewer.getId(), shared.getId());
        var session = study.start(viewer.getId(), shared.getId(), StudyMode.SCHEDULED_REVIEW);
        assertFalse(session.deck().ownDeck());
        var answer = session.cards().getFirst();
        study.hint(viewer.getId(), session.id(), answer.id());
        study.submit(viewer.getId(), session.id(), answer.id(), new SubmitStudyAnswerRequest("Answer", false));
        study.complete(viewer.getId(), session.id());
        var personal = progress.findByUser_IdAndFlashcard_Id(viewer.getId(), card.getId()).orElseThrow();
        assertEquals(1, personal.getAttempts());
        assertEquals(4, progress.findByUser_IdAndFlashcard_Id(author.getId(), card.getId()).orElseThrow().getMasteryLevel());
        var practice = study.start(viewer.getId(), shared.getId(), StudyMode.FREE_PRACTICE);
        study.submit(viewer.getId(), practice.id(), practice.cards().getFirst().id(), new SubmitStudyAnswerRequest("Answer", false));
        assertEquals(personal.getNextReviewAt(), progress.findByUser_IdAndFlashcard_Id(viewer.getId(), card.getId()).orElseThrow().getNextReviewAt());
        assertEquals(1, progress.findByUser_IdAndFlashcard_Id(viewer.getId(), card.getId()).orElseThrow().getAttempts());
        try (var client = new Client()) {
            client.login(viewer);
            assertEquals(200, client.getPath("/api/decks/" + shared.getId() + "/study-access").statusCode());
            var report = client.getPath("/api/statistics/me/decks/" + shared.getId());
            assertEquals(200, report.statusCode(), report.body());
            assertEquals(personal.getMasteryLevel() * 25, json.readTree(report.body()).get("masteryPercent").asInt());
            follows.unfollow(viewer.getId(), shared.getId());
            assertEquals(404, client.getPath("/api/decks/" + shared.getId() + "/study-access").statusCode());
            assertEquals(404, client.getPath("/api/statistics/me/decks/" + shared.getId()).statusCode());
        }
        assertNotFound(() -> study.getOwned(viewer.getId(), practice.id()));
        assertNotFound(() -> study.hint(viewer.getId(), practice.id(), practice.cards().getFirst().id()));
        assertNotFound(() -> study.submit(viewer.getId(), practice.id(), practice.cards().getFirst().id(), new SubmitStudyAnswerRequest("Answer", false)));
        assertNotFound(() -> study.complete(viewer.getId(), practice.id()));
        assertNotFound(() -> study.getSummary(viewer.getId(), session.id()));
        follows.follow(viewer.getId(), shared.getId());
        assertEquals(personal.getNextReviewAt(), progress.findByUser_IdAndFlashcard_Id(viewer.getId(), card.getId()).orElseThrow().getNextReviewAt());
        assertEquals(practice.id(), study.getOwned(viewer.getId(), practice.id()).id());
    }

    @Test void privateDecksHideFollowsAndBlockStudyWhileDeletedAuthorsRemainStudyable() throws Exception {
        var viewer = account();
        var author = account();
        var shared = deck(author, "Shared", true, DeckType.GENERAL, "English", null, null);
        addCards(shared, 1);
        follows.follow(viewer.getId(), shared.getId());
        var session = study.start(viewer.getId(), shared.getId(), StudyMode.FREE_PRACTICE);
        shared.setPublic(false); decks.saveAndFlush(shared);
        assertNotFound(() -> study.start(viewer.getId(), shared.getId(), StudyMode.FREE_PRACTICE));
        assertNotFound(() -> study.getOwned(viewer.getId(), session.id()));
        try (var client = new Client()) {
            client.login(viewer);
            assertEquals(0, json.readTree(client.getPath("/api/decks/library").body()).get("totalElements").asInt());
            assertEquals(204, client.send("DELETE", "/api/explore/decks/" + shared.getId() + "/follow", "", true).statusCode());
            shared.setPublic(true); decks.saveAndFlush(shared);
            follows.follow(viewer.getId(), shared.getId());
            deletion.deleteAccount(author.getId());
            assertEquals("Deleted user", client.page("/" + shared.getId()).get("author").get("displayName").asText());
            assertEquals(1, json.readTree(client.getPath("/api/decks/library").body()).get("totalElements").asInt());
            assertEquals(1, study.start(viewer.getId(), shared.getId(), StudyMode.FREE_PRACTICE).totalCards());
            assertEquals(1, study.start(viewer.getId(), shared.getId(), StudyMode.SCHEDULED_REVIEW).totalCards());
            assertEquals(200, client.getPath("/api/statistics/me/decks/" + shared.getId()).statusCode());
        }
        deletion.deleteAccount(viewer.getId());
        try (var client = new Client()) {
            client.login(account());
            assertEquals(0, client.page("/" + shared.getId()).get("followerCount").asInt());
        }
    }

    private void assertNotFound(Runnable action) {
        assertEquals(404, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
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
            return getPath("/api/explore/decks" + query);
        }
        HttpResponse<String> getPath(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> send(String method, String path, String body, boolean protectedRequest) throws Exception {
            var request = HttpRequest.newBuilder(endpoint(path)).header("Content-Type", "application/json");
            if (protectedRequest) {
                var token = json.readTree(getPath("/api/auth/csrf").body());
                request.header(token.get("headerName").asText(), token.get("token").asText());
            }
            return http.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonNode page(String query) throws Exception {
            var response = get(query); assertEquals(200, response.statusCode(), response.body()); return json.readTree(response.body());
        }
        @Override public void close() { http.close(); }
    }
}
