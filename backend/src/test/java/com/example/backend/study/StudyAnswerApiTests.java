package com.example.backend.study;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
class StudyAnswerApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "StudyAnswers123!";
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
    void savesHintRawTextServerResultAndProgressOnceThenSkipsNextCard() throws Exception {
        var fixture = fixture(2);
        var first = fixture.answers().getFirst();
        var second = fixture.answers().getLast();
        var input = Map.of("userAnswer", "  CIEKAWOŚĆ  ", "correct", false, "usedHint", false);
        try (var client = new Client()) {
            client.login(fixture.user());
            var hint = client.post(path(fixture, first) + "/hint", Map.of(), true);
            assertEquals(200, hint.statusCode(), hint.body());
            var hintBody = json.readTree(hint.body());
            assertEquals("cie", hintBody.get("hint").asText());
            assertTrue(hintBody.get("usedHint").asBoolean());
            assertTrue(hintBody.get("result").isNull());
            assertFalse(hintBody.has("answer"));
            assertEquals(0, progress.count());
            assertEquals("cie", json.readTree(client.get(fixture.session().getId()).body()).get("cards").get(0).get("hint").asText());

            var response = client.post(path(fixture, first), input, true);
            assertEquals(200, response.statusCode(), response.body());
            var result = json.readTree(response.body()).get("result");
            assertTrue(result.get("correct").asBoolean());
            assertTrue(result.get("usedHint").asBoolean());
            assertEquals("  CIEKAWOŚĆ  ", result.get("userAnswer").asText());
            assertEquals("ciekawość, ciekawosc", result.get("answer").asText());
            assertEquals("Translation", result.get("translatedExample").asText());
            var state = progress.findByUser_IdAndFlashcard_Id(fixture.user().getId(), first.getFlashcard().getId()).orElseThrow();
            assertEquals(1, state.getAttempts());
            assertEquals(1, state.getCorrectCount());
            assertEquals(2, state.getMasteryLevel());
            assertEquals(3, ChronoUnit.DAYS.between(state.getLastAnsweredAt(), state.getNextReviewAt()));
            assertEquals("  CIEKAWOŚĆ  ", answers.findById(first.getId()).orElseThrow().getUserAnswer());

            assertEquals(200, client.post(path(fixture, first), input, true).statusCode());
            assertEquals(1, progress.findById(state.getId()).orElseThrow().getAttempts());
            assertEquals(409, client.post(path(fixture, first), Map.of("userAnswer", "changed"), true).statusCode());
            assertEquals(409, client.post(path(fixture, first) + "/hint", Map.of(), true).statusCode());
            var reloaded = json.readTree(client.get(fixture.session().getId()).body()).get("cards");
            assertTrue(reloaded.get(0).get("result").get("correct").asBoolean());
            assertTrue(reloaded.get(1).get("result").isNull());
            assertFalse(reloaded.get(1).has("answer"));

            var skipped = client.post(path(fixture, second), Map.of("userAnswer", "ignored", "skipped", true, "correct", true), true);
            assertEquals(200, skipped.statusCode(), skipped.body());
            var skipResult = json.readTree(skipped.body()).get("result");
            assertTrue(skipResult.get("skipped").asBoolean());
            assertFalse(skipResult.get("correct").asBoolean());
            assertEquals("", skipResult.get("userAnswer").asText());
            var skipProgress = progress.findByUser_IdAndFlashcard_Id(fixture.user().getId(), second.getFlashcard().getId()).orElseThrow();
            assertEquals(1, skipProgress.getWrongCount());
            assertEquals(1, ChronoUnit.DAYS.between(skipProgress.getLastAnsweredAt(), skipProgress.getNextReviewAt()));
            assertEquals(StudySessionStatus.ACTIVE, sessions.findById(fixture.session().getId()).orElseThrow().getStatus());
        }
    }

    @Test
    void rejectsMissingSessionCsrfForeignAnswersWrongOrderInvalidInputAndEndedSessions() throws Exception {
        var own = fixture(2);
        var foreign = fixture(1);
        var sameUserOtherSession = sessions.saveAndFlush(new StudySession(own.user(), own.deck(), 1, Instant.now().minusSeconds(30)));
        var otherAnswer = answers.saveAndFlush(new StudyAnswer(sameUserOtherSession, own.answers().getFirst().getFlashcard(), 1));
        try (var client = new Client()) {
            assertEquals(401, client.post(path(own, own.answers().getFirst()), Map.of("userAnswer", "x"), true).statusCode());
            client.login(own.user());
            assertEquals(403, client.post(path(own, own.answers().getFirst()), Map.of("userAnswer", "x"), false).statusCode());
            assertEquals(403, client.post(path(own, own.answers().getFirst()) + "/hint", Map.of(), false).statusCode());
            assertEquals(404, client.post(path(foreign, foreign.answers().getFirst()), Map.of("userAnswer", "x"), true).statusCode());
            assertEquals(404, client.post(path(foreign, foreign.answers().getFirst()) + "/hint", Map.of(), true).statusCode());
            String mismatch = "/api/study-sessions/" + own.session().getId() + "/answers/" + otherAnswer.getId();
            assertEquals(404, client.post(mismatch, Map.of("userAnswer", "x"), true).statusCode());
            assertEquals(409, client.post(path(own, own.answers().getLast()), Map.of("userAnswer", "x"), true).statusCode());
            assertEquals(409, client.post(path(own, own.answers().getLast()) + "/hint", Map.of(), true).statusCode());
            assertEquals(400, client.post(path(own, own.answers().getFirst()), Map.of("userAnswer", "x".repeat(2001)), true).statusCode());
            assertEquals(400, client.post(path(own, own.answers().getFirst()), Map.of(), true).statusCode());
            var ended = sessions.findById(own.session().getId()).orElseThrow();
            ended.abandon(Instant.now());
            sessions.saveAndFlush(ended);
            assertEquals(409, client.post(path(own, own.answers().getFirst()), Map.of("userAnswer", "x"), true).statusCode());
            assertEquals(409, client.post(path(own, own.answers().getFirst()) + "/hint", Map.of(), true).statusCode());
            assertEquals(0, progress.count());
            assertFalse(answers.findById(own.answers().getFirst().getId()).orElseThrow().isAnswered());
        }
    }

    @Test
    void concurrentRetriesSaveOnlyOneAttempt() throws Exception {
        var fixture = fixture(1);
        String path = path(fixture, fixture.answers().getFirst());
        try (var client = new Client()) {
            client.login(fixture.user());
            client.csrf(); // Initialize the rotated token before the concurrent requests.
            var first = CompletableFuture.supplyAsync(() -> client.answerUnchecked(path));
            var second = CompletableFuture.supplyAsync(() -> client.answerUnchecked(path));
            assertEquals(200, first.get(20, TimeUnit.SECONDS).statusCode());
            assertEquals(200, second.get(20, TimeUnit.SECONDS).statusCode());
            var state = progress.findByUser_IdAndFlashcard_Id(fixture.user().getId(), fixture.answers().getFirst().getFlashcard().getId()).orElseThrow();
            assertEquals(1, state.getAttempts());
            assertEquals(1, state.getCorrectCount());
        }
    }

    @Test
    void completesOnlyAfterAllAnswersAndReturnsPersistedSummaryOnReloadAndRetry() throws Exception {
        var fixture = fixture(3);
        String base = "/api/study-sessions/" + fixture.session().getId();
        try (var client = new Client()) {
            client.login(fixture.user());
            assertEquals(409, client.post(base + "/complete", Map.of(), true).statusCode());
            assertEquals(409, client.getPath(base + "/summary").statusCode());
            var first = fixture.answers().get(0);
            assertEquals(200, client.post(path(fixture, first) + "/hint", Map.of(), true).statusCode());
            assertEquals(200, client.post(path(fixture, first), Map.of("userAnswer", "ciekawość"), true).statusCode());
            assertEquals(409, client.post(base + "/complete", Map.of(), true).statusCode());
            assertEquals(StudySessionStatus.ACTIVE, sessions.findById(fixture.session().getId()).orElseThrow().getStatus());
            assertEquals(200, client.post(path(fixture, fixture.answers().get(1)), Map.of("userAnswer", "", "skipped", true), true).statusCode());
            assertEquals(200, client.post(path(fixture, fixture.answers().get(2)), Map.of("userAnswer", "ciekawosc"), true).statusCode());
            // Scores supplied by a client are ignored: results come from saved answers.
            var completed = client.post(base + "/complete", Map.of("correctCards", 999, "xpEarned", 999), true);
            assertEquals(200, completed.statusCode(), completed.body());
            assertTrue(completed.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            var result = json.readTree(completed.body());
            assertEquals(fixture.session().getId().longValue(), result.get("sessionId").asLong());
            assertEquals(fixture.deck().getId().longValue(), result.get("deckId").asLong());
            assertEquals(3, result.get("totalCards").asInt());
            assertEquals(2, result.get("correctCards").asInt());
            assertEquals(1, result.get("wrongCards").asInt());
            assertEquals(67, result.get("accuracy").asInt());
            assertEquals(15, result.get("xpEarned").asInt());
            var saved = sessions.findById(fixture.session().getId()).orElseThrow();
            assertEquals(StudySessionStatus.COMPLETED, saved.getStatus());
            assertEquals(saved.getEndedAt(), Instant.parse(result.get("endedAt").asText()));
            assertEquals(java.time.Duration.between(saved.getStartedAt(), saved.getEndedAt()).getSeconds(), result.get("durationSeconds").asLong());
            var reloaded = client.getPath(base + "/summary");
            assertEquals(200, reloaded.statusCode(), reloaded.body());
            assertEquals(result, json.readTree(reloaded.body()));
            assertEquals(result, json.readTree(client.post(base + "/complete", Map.of(), true).body()));
            assertEquals("COMPLETED", json.readTree(client.get(fixture.session().getId()).body()).get("status").asText());
            assertEquals(3, progress.count());
            assertTrue(progress.findAll().stream().allMatch(state -> state.getAttempts() == 1));
        }
    }

    @Test
    void protectsCompletionAndSummaryAndRejectsAbandonedSessions() throws Exception {
        var own = fixture(1);
        var foreign = fixture(1);
        String base = "/api/study-sessions/" + own.session().getId();
        String other = "/api/study-sessions/" + foreign.session().getId();
        try (var client = new Client()) {
            assertEquals(401, client.getPath(base + "/summary").statusCode());
            assertEquals(401, client.post(base + "/complete", Map.of(), true).statusCode());
            client.login(own.user());
            assertEquals(403, client.post(base + "/complete", Map.of(), false).statusCode());
            assertEquals(404, client.post(other + "/complete", Map.of(), true).statusCode());
            assertEquals(404, client.getPath(other + "/summary").statusCode());
            assertEquals(404, client.getPath("/api/study-sessions/9223372036854775807/summary").statusCode());
            var abandoned = sessions.findById(own.session().getId()).orElseThrow();
            abandoned.abandon(Instant.now());
            sessions.saveAndFlush(abandoned);
            assertEquals(409, client.post(base + "/complete", Map.of(), true).statusCode());
            assertEquals(409, client.getPath(base + "/summary").statusCode());
            assertEquals(StudySessionStatus.ABANDONED, sessions.findById(own.session().getId()).orElseThrow().getStatus());
        }
    }

    @Test
    void concurrentCompletionReturnsTheSameSummaryWithoutUpdatingProgressAgain() throws Exception {
        var fixture = fixture(1);
        String base = "/api/study-sessions/" + fixture.session().getId();
        try (var client = new Client()) {
            client.login(fixture.user());
            assertEquals(200, client.post(path(fixture, fixture.answers().getFirst()), Map.of("userAnswer", "ciekawość"), true).statusCode());
            client.csrf();
            var first = CompletableFuture.supplyAsync(() -> client.completeUnchecked(base));
            var second = CompletableFuture.supplyAsync(() -> client.completeUnchecked(base));
            var firstResponse = first.get(20, TimeUnit.SECONDS);
            var secondResponse = second.get(20, TimeUnit.SECONDS);
            assertEquals(200, firstResponse.statusCode(), firstResponse.body());
            assertEquals(200, secondResponse.statusCode(), secondResponse.body());
            assertEquals(json.readTree(firstResponse.body()), json.readTree(secondResponse.body()));
            assertEquals(1, progress.findAll().getFirst().getAttempts());
        }
    }

    private record Fixture(User user, Deck deck, StudySession session, List<StudyAnswer> answers) {}
    private Fixture fixture(int count) {
        String name = "Answer" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        users.saveAndFlush(user);
        var deck = decks.saveAndFlush(new Deck(user, "Deck", DeckType.GENERAL, "English", null));
        var session = sessions.saveAndFlush(new StudySession(user, deck, count, Instant.now().minusSeconds(30)));
        var selected = new ArrayList<StudyAnswer>();
        for (int i = 0; i < count; i++) {
            var card = new Flashcard(deck, "Question " + i, "ciekawość, ciekawosc");
            card.setTranslatedExample("Translation");
            cards.saveAndFlush(card);
            selected.add(answers.saveAndFlush(new StudyAnswer(session, card, i + 1)));
        }
        return new Fixture(user, deck, session, selected);
    }
    private String path(Fixture fixture, StudyAnswer answer) { return "/api/study-sessions/" + fixture.session().getId() + "/answers/" + answer.getId(); }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }

    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        private String csrf() throws Exception {
            var response = http.send(HttpRequest.newBuilder(endpoint("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            var response = post("/api/auth/login", Map.of("login", user.getUsername(), "password", PASSWORD), true);
            assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> post(String path, Map<String, ?> body, boolean protectedRequest) throws Exception {
            var request = HttpRequest.newBuilder(endpoint(path)).header("Content-Type", "application/json");
            if (protectedRequest) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> answerUnchecked(String path) {
            try { return post(path, Map.of("userAnswer", "ciekawość"), true); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        }
        HttpResponse<String> get(Long id) throws Exception {
            return getPath("/api/study-sessions/" + id);
        }
        HttpResponse<String> getPath(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> completeUnchecked(String base) {
            try { return post(base + "/complete", Map.of(), true); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        }
        @Override public void close() { http.close(); }
    }
}
