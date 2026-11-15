package com.example.backend.statistics;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.example.backend.deck.*;
import com.example.backend.flashcard.*;
import com.example.backend.study.*;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatisticsApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "StatisticsPass123!";
    private static final Instant NOW = Instant.parse("2026-11-15T23:30:00Z"); // Already Nov 16 in Warsaw.
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired FlashcardRepository cards;
    @Autowired StudySessionRepository sessions;
    @Autowired StudyAnswerRepository answers;
    @Autowired FlashcardProgressRepository progress;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;
    @MockitoBean Clock clock;

    @BeforeEach void time() { when(clock.instant()).thenReturn(NOW); }

    @Test void emptyAccountGetsZeroStatisticsAndACompleteCalendar() throws Exception {
        try (var client = new Client()) {
            assertEquals(401, client.get("").statusCode());
            client.login(account());
            var response = client.get("");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var data = json.readTree(response.body());
            assertEquals("2026-11-16", data.get("today").asText());
            assertEquals(365, data.get("activity").size());
            assertEquals(0, data.get("profile").get("totalXp").asLong());
            assertEquals(1, data.get("profile").get("level").asInt());
            assertEquals(0, data.get("periods").get("all").get("accuracy").asInt());
            assertEquals(0, data.get("decks").size());
        }
    }

    @Test void aggregatesRealAnswersAcrossMidnightAndKeepsFreePracticeSeparateFromMastery() throws Exception {
        var owner = account();
        var deck = deck(owner);
        var first = cards.saveAndFlush(new Flashcard(deck, "Door", "Drzwi"));
        var second = cards.saveAndFlush(new Flashcard(deck, "Apple", "Jabłko"));
        cards.saveAndFlush(new Flashcard(deck, "Unstudied", "Nowa"));
        var start = Instant.parse("2026-11-15T22:58:00Z");
        var before = Instant.parse("2026-11-15T22:59:00Z");
        var after = Instant.parse("2026-11-15T23:01:00Z");
        var session = sessions.saveAndFlush(new StudySession(owner, deck, 2, start, StudyMode.SCHEDULED_REVIEW));
        saveAnswer(session, first, 1, true, false, false, before);
        saveAnswer(session, second, 2, true, true, false, after);
        session.complete(Instant.parse("2026-11-15T23:05:00Z")); sessions.saveAndFlush(session);
        var mastered = new FlashcardProgress(owner, first);
        mastered.recordReview(true, 4, 0, before, before.plusSeconds(14 * 86400)); progress.saveAndFlush(mastered);
        var average = new FlashcardProgress(owner, second);
        average.recordReview(true, 2, 0, after, after.plusSeconds(3 * 86400)); progress.saveAndFlush(average);
        var freeStart = Instant.parse("2026-11-15T23:10:00Z");
        var free = sessions.saveAndFlush(new StudySession(owner, deck, 2, freeStart, StudyMode.FREE_PRACTICE));
        saveAnswer(free, first, 1, true, false, false, freeStart.plusSeconds(20));
        saveAnswer(free, second, 2, false, false, true, freeStart.plusSeconds(40));
        free.complete(freeStart.plusSeconds(120)); sessions.saveAndFlush(free);
        var other = account();
        var otherDeck = deck(other);
        var otherSession = sessions.saveAndFlush(new StudySession(other, otherDeck, 3, freeStart, StudyMode.FREE_PRACTICE));
        for (int index = 1; index <= 3; index++) {
            var card = cards.saveAndFlush(new Flashcard(otherDeck, "Other user's card " + index, "Answer"));
            saveAnswer(otherSession, card, index, true, false, false, freeStart.plusSeconds(index));
        }
        otherSession.complete(freeStart.plusSeconds(60)); sessions.saveAndFlush(otherSession);
        try (var client = new Client()) {
            client.login(owner);
            var response = client.get("");
            assertEquals(200, response.statusCode(), response.body());
            var data = json.readTree(response.body());
            var day = data.get("periods").get("day");
            assertEquals(3, day.get("studied").asInt());
            assertEquals(2, day.get("correct").asInt());
            assertEquals(67, day.get("accuracy").asInt());
            assertEquals(15, day.get("xp").asLong());
            assertEquals(540, day.get("studySeconds").asLong());
            assertEquals(0, day.get("mastered").asLong());
            assertEquals(25, data.get("profile").get("totalXp").asLong());
            assertEquals(2, data.get("profile").get("globalRank").asLong());
            assertEquals(1, data.get("periods").get("all").get("mastered").asLong());
            assertEquals(50, data.get("decks").get(0).get("masteryPercent").asInt());
            assertEquals(1, data.get("decks").size());
            var firstPage = json.readTree(client.get("/decks/" + deck.getId() + "?size=1").body());
            assertEquals(first.getId().longValue(), firstPage.get("content").get(0).get("id").asLong());
            assertEquals(3, firstPage.get("totalPages").asInt());
            var nextPage = json.readTree(client.get("/decks/" + deck.getId() + "?size=1&page=1").body());
            assertEquals(second.getId().longValue(), nextPage.get("content").get(0).get("id").asLong());
            var report = json.readTree(client.get("/decks/" + deck.getId() + "?stars=1&size=1").body());
            assertEquals(3, report.get("total").asLong());
            assertEquals(1, report.get("totalElements").asLong());
            assertEquals(0, report.get("content").get(0).get("masteryLevel").asInt());
            assertTrue(report.get("content").get(0).get("lastReviewedAt").isNull());
        }
    }

    @Test void reportIsOwnerOnlyAndValidatesFiltersAndPagination() throws Exception {
        var owner = account();
        var own = deck(owner);
        var foreign = deck(account());
        foreign.setPublic(true); decks.saveAndFlush(foreign);
        try (var client = new Client()) {
            assertEquals(401, client.get("/decks/" + own.getId()).statusCode());
            client.login(owner);
            assertEquals(404, client.get("/decks/" + foreign.getId()).statusCode());
            for (String query : new String[]{"?stars=5", "?sort=invalid", "?page=-1", "?size=0", "?size=101"}) {
                assertEquals(400, client.get("/decks/" + own.getId() + query).statusCode(), query);
            }
            var list = json.readTree(client.get("/decks").body());
            assertEquals(1, list.size());
            assertEquals(own.getId().longValue(), list.get(0).get("id").asLong());
            var empty = json.readTree(client.get("/decks/" + own.getId()).body());
            assertEquals(0, empty.get("total").asLong());
            assertEquals(0, empty.get("content").size());
        }
    }

    private void saveAnswer(StudySession session, Flashcard card, int position, boolean correct, boolean hint, boolean skipped, Instant time) {
        var answer = new StudyAnswer(session, card, position);
        if (hint) answer.markHintUsed();
        answer.recordAnswer(skipped ? "" : card.getAnswer(), correct, skipped, time);
        answers.saveAndFlush(answer);
    }
    private User account() {
        String name = "Stats" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(NOW); return users.saveAndFlush(user);
    }
    private Deck deck(User owner) { return decks.saveAndFlush(new Deck(owner, "Statistics deck", DeckType.GENERAL, "English", null)); }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        void login(User user) throws Exception {
            var csrf = http.send(HttpRequest.newBuilder(endpoint("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            var token = json.readTree(csrf.body());
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).header("Content-Type", "application/json")
                    .header(token.get("headerName").asText(), token.get("token").asText())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            assertEquals(200, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint("/api/statistics/me" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
