package com.example.backend.admin;

import com.example.backend.deck.*;
import com.example.backend.deck.follow.DeckFollowRepository;
import com.example.backend.deck.follow.DeckFollowService;
import com.example.backend.flashcard.*;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.study.*;
import com.example.backend.study.dto.request.SubmitStudyAnswerRequest;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.*;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminDeckApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "AdminDeckTests123!";
    @Autowired UserRepository users;
    @Autowired UserDeletionService deletion;
    @Autowired RoleRepository roles;
    @Autowired DeckRepository decks;
    @Autowired FlashcardRepository cards;
    @Autowired DeckFollowService follows;
    @Autowired DeckFollowRepository followRows;
    @Autowired StudyService study;
    @Autowired StudySessionRepository sessions;
    @Autowired StudyAnswerRepository answers;
    @Autowired FlashcardProgressRepository progress;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test void listsOnlyPublicDecksWithRealAuthorsCountsFiltersAndPagination() throws Exception {
        var admin = account(true); var owner = account(false); var removed = account(false);
        deck(admin, "Own public", true, DeckType.GENERAL);
        var shared = deck(owner, "English_under 100%", true, DeckType.LANGUAGE);
        shared.setDescription("Beginner phrases"); decks.saveAndFlush(shared);
        cards.saveAndFlush(new Flashcard(shared, "Question", "Answer"));
        cards.saveAndFlush(new Flashcard(shared, "Another question", "Answer"));
        follows.follow(admin.getId(), shared.getId());
        var orphan = deck(removed, "History", true, DeckType.GENERAL);
        deck(owner, "Hidden private", false, DeckType.GENERAL);
        deck(admin, "Own private", false, DeckType.GENERAL);
        deletion.deleteAccount(removed.getId());
        try (var client = new Client()) {
            client.login(admin);
            var response = client.get("/api/admin/decks?size=2");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var page = json.readTree(response.body());
            assertEquals(3, page.get("totalElements").asInt());
            assertEquals(2, page.get("totalPages").asInt());
            assertEquals(2, page.get("content").size());
            assertEquals(orphan.getId().longValue(), page.get("content").get(0).get("id").asLong());
            assertTrue(page.get("content").get(0).get("author").get("id").isNull());
            assertEquals("Deleted user", page.get("content").get(0).get("author").get("displayName").asText());
            assertEquals(2, page.get("summary").get("users").asInt());
            assertEquals(3, page.get("summary").get("publicDecks").asInt());
            var item = client.page("?search=" + encoded(owner.getUsername().toUpperCase())).get("content").get(0);
            assertEquals(shared.getId().longValue(), item.get("id").asLong());
            assertEquals(2, item.get("cardCount").asInt());
            assertEquals(1, item.get("followerCount").asInt());
            assertEquals(owner.getUsername(), item.get("author").get("displayName").asText());
            assertEquals(1, client.page("?deck_type=LANGUAGE&search=phrases").get("totalElements").asInt());
            assertEquals(1, client.page("?search=%25").get("totalElements").asInt());
            assertEquals(1, client.page("?search=_").get("totalElements").asInt());
            assertEquals(0, client.page("?search=private").get("totalElements").asInt());
            assertEquals(1, client.page("?size=2&page=1").get("content").size());
            assertEquals(0, client.page("?page=99").get("content").size());
            assertFalse(response.body().contains(owner.getEmail()));
            assertFalse(response.body().contains("password"));
            for (String query : new String[]{"?page=-1", "?page=1000001", "?page=abc", "?size=0", "?size=51", "?deck_type=INVALID", "?search=" + "x".repeat(121)}) {
                assertEquals(400, client.get("/api/admin/decks" + query).statusCode(), query);
            }
        }
    }

    @Test void deletingPublicDeckCascadesCardsFollowsSessionsAnswersAndPersonalProgress() throws Exception {
        var admin = account(true); var owner = account(false); var learner = account(false);
        var shared = deck(owner, "Shared to remove", true, DeckType.GENERAL);
        var savedCard = cards.saveAndFlush(new Flashcard(shared, "Question", "Answer"));
        var retained = deck(owner, "Private retained", false, DeckType.GENERAL);
        var retainedCard = cards.saveAndFlush(new Flashcard(retained, "Keep question", "Keep answer"));
        follows.follow(learner.getId(), shared.getId());
        var session = study.start(learner.getId(), shared.getId(), StudyMode.SCHEDULED_REVIEW);
        var answer = session.cards().getFirst();
        study.submit(learner.getId(), session.id(), answer.id(), new SubmitStudyAnswerRequest("Answer", false));
        study.complete(learner.getId(), session.id());
        assertTrue(progress.findByUser_IdAndFlashcard_Id(learner.getId(), savedCard.getId()).isPresent());
        try (var client = new Client()) {
            client.login(admin);
            assertEquals(403, client.remove(shared.getId(), false).statusCode());
            assertTrue(decks.existsById(shared.getId()));
            var response = client.remove(shared.getId(), true);
            assertEquals(204, response.statusCode(), response.body());
            assertEquals("", response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(decks.existsById(shared.getId()));
            assertFalse(cards.existsById(savedCard.getId()));
            assertFalse(followRows.existsByUser_IdAndDeck_Id(learner.getId(), shared.getId()));
            assertFalse(progress.findByUser_IdAndFlashcard_Id(learner.getId(), savedCard.getId()).isPresent());
            assertFalse(sessions.existsById(session.id()));
            assertFalse(answers.existsById(answer.id()));
            assertTrue(decks.existsById(retained.getId()));
            assertTrue(cards.existsById(retainedCard.getId()));
            assertTrue(users.findById(owner.getId()).orElseThrow().isEnabled());
            assertTrue(users.existsById(learner.getId()));
            assertEquals(404, client.get("/api/explore/decks/" + shared.getId()).statusCode());
            assertEquals(0, client.page("").get("totalElements").asInt());
            assertEquals(0, client.page("").get("summary").get("publicDecks").asInt());
            assertEquals(404, client.remove(shared.getId(), true).statusCode());
        }
    }

    @Test void requiresAdminAndRejectsPrivateOrNoLongerPublicDecks() throws Exception {
        var admin = account(true); var learner = account(false);
        var shared = deck(learner, "Initially public", true, DeckType.GENERAL);
        var privateDeck = deck(admin, "Own private", false, DeckType.GENERAL);
        try (var client = new Client()) {
            assertEquals(401, client.get("/api/admin/decks").statusCode());
            client.login(learner);
            assertEquals(403, client.get("/api/admin/decks").statusCode());
            assertEquals(403, client.remove(shared.getId(), true).statusCode());
            assertTrue(decks.existsById(shared.getId()));
            client.login(admin);
            assertEquals(1, client.page("").get("totalElements").asInt());
            shared.setPublic(false); decks.saveAndFlush(shared);
            assertEquals(404, client.remove(shared.getId(), true).statusCode());
            assertEquals(404, client.remove(privateDeck.getId(), true).statusCode());
            assertEquals(404, client.remove(Long.MAX_VALUE, true).statusCode());
            assertEquals(400, client.remove(0L, true).statusCode());
            assertTrue(decks.existsById(shared.getId()));
            assertTrue(decks.existsById(privateDeck.getId()));
        }
    }

    @Test void canModerateAuthorlessDecksAndOwnPublicDecks() throws Exception {
        var admin = account(true); var removed = account(false);
        var orphan = deck(removed, "Orphaned public", true, DeckType.LANGUAGE);
        var card = cards.saveAndFlush(new Flashcard(orphan, "Question", "Answer"));
        follows.follow(admin.getId(), orphan.getId());
        deletion.deleteAccount(removed.getId());
        var owned = deck(admin, "Own public", true, DeckType.GENERAL);
        try (var client = new Client()) {
            client.login(admin);
            assertEquals(2, client.page("").get("totalElements").asInt());
            assertEquals(204, client.remove(orphan.getId(), true).statusCode());
            assertFalse(cards.existsById(card.getId()));
            assertFalse(followRows.existsByUser_IdAndDeck_Id(admin.getId(), orphan.getId()));
            assertEquals(204, client.remove(owned.getId(), true).statusCode());
            assertEquals(0, client.page("").get("content").size());
            assertEquals(0, client.page("").get("totalPages").asInt());
            assertTrue(users.existsById(admin.getId()));
        }
    }

    @Test void editsPublicDeckDetailsWithoutChangingAuthorCardsOrStudyData() throws Exception {
        var admin = account(true); var owner = account(false); var removed = account(false);
        var shared = deck(owner, "Original", true, DeckType.GENERAL);
        var card = cards.saveAndFlush(new Flashcard(shared, "Question", "Answer"));
        follows.follow(admin.getId(), shared.getId());
        var session = study.start(admin.getId(), shared.getId(), StudyMode.FREE_PRACTICE);
        var orphan = deck(removed, "Orphan", true, DeckType.GENERAL);
        deletion.deleteAccount(removed.getId());
        String body = editBody("  Revised deck  ", "LANGUAGE", "Polish", "English", true);
        try (var client = new Client()) {
            client.login(admin);
            var loaded = client.get("/api/admin/decks/" + shared.getId());
            assertEquals(200, loaded.statusCode(), loaded.body());
            assertEquals(owner.getId().longValue(), json.readTree(loaded.body()).get("author").get("id").asLong());
            var saved = client.edit(shared.getId(), body, true);
            assertEquals(200, saved.statusCode(), saved.body());
            assertEquals("no-store", saved.headers().firstValue("Cache-Control").orElseThrow());
            var result = json.readTree(saved.body());
            assertEquals("Revised deck", result.get("name").asText());
            assertEquals("LANGUAGE", result.get("deckType").asText());
            assertEquals("Polish", result.get("sourceLanguage").asText());
            assertEquals("English", result.get("targetLanguage").asText());
            assertEquals("Updated description", result.get("description").asText());
            assertEquals("Tech", result.get("category").asText());
            assertEquals("https://example.invalid/background.png", result.get("backgroundUrl").asText());
            assertTrue(result.get("publicDeck").asBoolean());
            assertFalse(result.get("ownDeck").asBoolean());
            assertEquals(shared.getCreatedAt().toEpochMilli(), Instant.parse(result.get("createdAt").asText()).toEpochMilli());
            assertEquals(owner.getId().longValue(), result.get("author").get("id").asLong());
            assertEquals("Question", cards.findById(card.getId()).orElseThrow().getQuestion());
            assertTrue(followRows.existsByUser_IdAndDeck_Id(admin.getId(), shared.getId()));
            assertTrue(sessions.existsById(session.id()));
            assertEquals(1, client.page("?search=Revised").get("totalElements").asInt());
            assertEquals(0, client.page("?deck_type=GENERAL&search=Revised").get("totalElements").asInt());
            assertEquals(200, client.edit(orphan.getId(), body, true).statusCode());
            var authorless = json.readTree(client.get("/api/admin/decks/" + orphan.getId()).body());
            assertTrue(authorless.get("author").get("id").isNull());
            assertEquals("Deleted user", authorless.get("author").get("displayName").asText());
            var owned = deck(admin, "Own public edit", true, DeckType.LANGUAGE);
            assertEquals(200, client.edit(owned.getId(), editBody("Now general", "GENERAL", "English", "Polish", true), true).statusCode());
            assertTrue(json.readTree(client.get("/api/admin/decks/" + owned.getId()).body()).get("targetLanguage").isNull());
        }
    }

    @Test void editingRequiresAdminCsrfPublicDeckAndValidatedDetails() throws Exception {
        var admin = account(true); var learner = account(false);
        var shared = deck(learner, "Do not change", true, DeckType.GENERAL);
        var hidden = deck(admin, "Private", false, DeckType.GENERAL);
        String valid = editBody("Updated", "GENERAL", "English", "", true);
        try (var client = new Client()) {
            assertEquals(401, client.edit(shared.getId(), valid, true).statusCode());
            assertEquals(401, client.get("/api/admin/decks/" + shared.getId()).statusCode());
            client.login(learner);
            assertEquals(403, client.edit(shared.getId(), valid, true).statusCode());
            assertEquals(403, client.get("/api/admin/decks/" + shared.getId()).statusCode());
            client.login(admin);
            assertEquals(403, client.edit(shared.getId(), valid, false).statusCode());
            for (String body : new String[]{editBody(" ", "GENERAL", "English", "", true),
                    editBody("Updated", "LANGUAGE", "English", "", true),
                    editBody("Updated", "INVALID", "English", "", true),
                    valid.replace("https://example.invalid/background.png", "javascript:alert(1)"),
                    editBody("Updated", "GENERAL", "English", "", false)}) {
                assertEquals(400, client.edit(shared.getId(), body, true).statusCode(), body);
            }
            assertEquals("Do not change", decks.findById(shared.getId()).orElseThrow().getName());
            assertEquals(404, client.edit(hidden.getId(), valid, true).statusCode());
            assertEquals(404, client.get("/api/admin/decks/" + hidden.getId()).statusCode());
            assertEquals(404, client.edit(Long.MAX_VALUE, valid, true).statusCode());
            assertEquals(400, client.edit(0L, valid, true).statusCode());
            shared.setPublic(false); decks.saveAndFlush(shared);
            assertEquals(404, client.edit(shared.getId(), valid, true).statusCode());
        }
    }

    private String editBody(String name, String type, String source, String target, boolean publicDeck) throws Exception {
        return json.writeValueAsString(Map.of("name", name, "description", "Updated description", "deckType", type,
                "sourceLanguage", source, "targetLanguage", target, "category", "Tech",
                "backgroundUrl", "https://example.invalid/background.png", "publicDeck", publicDeck));
    }

    private User account(boolean admin) {
        String name = "AdminDeck" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        if (admin) user.addRole(roles.findByName(RoleName.ADMIN).orElseThrow());
        user.setEmailVerifiedAt(Instant.now()); return users.saveAndFlush(user);
    }
    private Deck deck(User owner, String name, boolean publicDeck, DeckType type) {
        var deck = new Deck(owner, name, type, "English", type == DeckType.LANGUAGE ? "Polish" : null);
        deck.setPublic(publicDeck); return decks.saveAndFlush(deck);
    }
    private String encoded(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonNode page(String query) throws Exception {
            var response = get("/api/admin/decks" + query); assertEquals(200, response.statusCode(), response.body()); return json.readTree(response.body());
        }
        String csrf() throws Exception {
            var response = get("/api/auth/csrf"); assertEquals(200, response.statusCode()); return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).timeout(Duration.ofSeconds(15)).header("X-CSRF-TOKEN", csrf()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString()); assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> edit(Long id, String body, boolean withToken) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/admin/decks/" + id)).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
            if (withToken) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> remove(Long id, boolean withToken) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/admin/decks/" + id)).timeout(Duration.ofSeconds(15));
            if (withToken) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.DELETE().build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
