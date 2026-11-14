package com.example.backend.study;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
import com.example.backend.flashcard.Flashcard;
import com.example.backend.flashcard.FlashcardRepository;
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
class StudyStartApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "StudyStart123!";
    @Autowired StudySessionRepository sessions;
    @Autowired StudyAnswerRepository answers;
    @Autowired FlashcardProgressRepository progress;
    @Autowired FlashcardRepository cards;
    @Autowired DeckRepository decks;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test
    void startsAtMostTenDueThenNewCardsAndReloadsSnapshotsWithoutExposingAnswers() throws Exception {
        var user = account();
        var deck = deck(user);
        for (int i = 0; i < 11; i++) card(deck, "New " + i);
        var due = card(deck, "Due question");
        due.setExampleSentence("Question example");
        due.setTranslatedExample("Answer translation");
        due.setImageUrl("https://example.invalid/question.jpg");
        cards.saveAndFlush(due);
        var future = card(deck, "Not due yet");
        Instant now = Instant.now();
        var dueProgress = new FlashcardProgress(user, due);
        dueProgress.recordReview(true, 2, 0, now.minus(5, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        progress.saveAndFlush(dueProgress);
        var futureProgress = new FlashcardProgress(user, future);
        futureProgress.recordReview(true, 2, 0, now, now.plus(30, ChronoUnit.DAYS));
        progress.saveAndFlush(futureProgress);
        card(deck(user), "Another owned deck");
        card(deck(account()), "Another person's deck");

        try (var client = new Client()) {
            client.login(user);
            var response = client.start(deck.getId(), true);
            assertEquals(201, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var body = json.readTree(response.body());
            Long id = body.get("id").asLong();
            assertEquals("/api/study-sessions/" + id, response.headers().firstValue("Location").orElseThrow());
            assertEquals(10, body.get("totalCards").asInt());
            assertEquals("ACTIVE", body.get("status").asText());
            assertEquals(deck.getId().longValue(), body.get("deck").get("id").asLong());
            assertEquals(10, body.get("cards").size());
            assertEquals(due.getId().longValue(), body.get("cards").get(0).get("flashcardId").asLong());
            for (int i = 0; i < 10; i++) {
                var question = body.get("cards").get(i);
                assertEquals(i + 1, question.get("position").asInt());
                assertNotEquals(future.getId().longValue(), question.get("flashcardId").asLong());
                for (String hidden : new String[]{"answer", "answerSnapshot", "translatedExample", "userAnswer", "correct"}) {
                    assertFalse(question.has(hidden), hidden);
                }
                assertTrue(question.get("result").isNull());
                assertTrue(question.get("hint").isNull());
                assertFalse(question.get("usedHint").asBoolean());
            }
            var selected = answers.findBySession_IdOrderByPositionAsc(id);
            assertEquals(10, selected.size());
            assertEquals("Saved correct answer", selected.getFirst().getAnswerSnapshot());
            assertNull(selected.getFirst().getUserAnswer());
            assertNull(selected.getFirst().getAnsweredAt());
            assertEquals(user.getId(), sessions.findById(id).orElseThrow().getUser().getId());

            due.setQuestion("Changed after start");
            cards.saveAndFlush(due);
            var loaded = client.get(id);
            assertEquals(200, loaded.statusCode(), loaded.body());
            assertEquals("Due question", json.readTree(loaded.body()).get("cards").get(0).get("question").asText());
            cards.deleteById(due.getId());
            cards.flush();
            var retained = json.readTree(client.get(id).body()).get("cards").get(0);
            assertTrue(retained.get("flashcardId").isNull());
            assertEquals("Due question", retained.get("question").asText());
            assertEquals("https://example.invalid/question.jpg", retained.get("imageUrl").asText());
            assertEquals(1, sessions.count()); // GET/reload never creates another session.
        }
    }

    @Test
    void rejectsEmptyOrNotDueDeckWithoutCreatingSessionOrAnswers() throws Exception {
        var user = account();
        var empty = deck(user);
        var notDue = deck(user);
        var card = card(notDue, "Later");
        var state = new FlashcardProgress(user, card);
        Instant now = Instant.now();
        state.recordReview(true, 2, 0, now, now.plus(30, ChronoUnit.DAYS));
        progress.saveAndFlush(state);
        try (var client = new Client()) {
            client.login(user);
            var response = client.start(empty.getId(), true);
            assertEquals(409, response.statusCode(), response.body());
            assertTrue(json.readTree(response.body()).get("message").asText().contains("Add at least one flashcard"));
            var future = client.start(notDue.getId(), true);
            assertEquals(409, future.statusCode(), future.body());
            assertTrue(json.readTree(future.body()).get("message").asText().contains("due for review"));
            assertEquals(0, sessions.count());
            assertEquals(0, answers.count());
        }
    }

    @Test
    void requiresSessionCsrfDeckOwnerAndStudySessionOwner() throws Exception {
        var owner = account();
        var own = deck(owner);
        card(own, "Own question");
        var other = account();
        var foreign = deck(other);
        card(foreign, "Foreign private question");
        var publicDeck = deck(other);
        publicDeck.setPublic(true);
        decks.saveAndFlush(publicDeck);
        card(publicDeck, "Foreign public question");
        var deleted = account();
        var orphan = deck(deleted);
        orphan.setPublic(true);
        decks.saveAndFlush(orphan);
        card(orphan, "Ownerless question");
        users.delete(deleted);
        users.flush();
        Long foreignSessionId;
        try (var client = new Client()) {
            assertEquals(401, client.start(own.getId(), true).statusCode());
            assertEquals(401, client.get(Long.MAX_VALUE).statusCode());
            client.login(other);
            var started = client.start(foreign.getId(), true);
            assertEquals(201, started.statusCode(), started.body());
            foreignSessionId = json.readTree(started.body()).get("id").asLong();
        }
        try (var client = new Client()) {
            client.login(owner);
            assertEquals(403, client.start(own.getId(), false).statusCode());
            for (Long id : new Long[]{foreign.getId(), publicDeck.getId(), orphan.getId(), Long.MAX_VALUE}) {
                assertEquals(404, client.start(id, true).statusCode());
            }
            assertEquals(404, client.get(foreignSessionId).statusCode());
            assertEquals(404, client.get(Long.MAX_VALUE).statusCode());
            assertEquals(1, sessions.count());
        }
    }

    private User account() {
        String name = "Study" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }
    private Deck deck(User owner) { return decks.saveAndFlush(new Deck(owner, "Study deck", DeckType.GENERAL, "English", null)); }
    private Flashcard card(Deck deck, String question) { return cards.saveAndFlush(new Flashcard(deck, question, "Saved correct answer")); }
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
        HttpResponse<String> start(Long deckId, boolean protectedRequest) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/decks/" + deckId + "/study-sessions"));
            if (protectedRequest) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> get(Long sessionId) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint("/api/study-sessions/" + sessionId)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
