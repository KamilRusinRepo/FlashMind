package com.example.backend.reminder;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
import com.example.backend.flashcard.Flashcard;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.mail.DailyReminderMailConsumer;
import com.example.backend.mail.DailyReminderRequested;
import com.example.backend.mail.MailConfiguration;
import com.example.backend.study.*;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DailyReminderTests extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-14T17:05:00Z"); // 18:05 in Warsaw.
    private static final String PASSWORD = "Reminders123!";
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired FlashcardRepository cards;
    @Autowired StudySessionRepository sessions;
    @Autowired StudyAnswerRepository answers;
    @Autowired FlashcardProgressRepository progress;
    @Autowired DailyReminderRepository reminders;
    @Autowired DailyReminderService preparation;
    @Autowired DailyReminderPublisher publisher;
    @Autowired DailyReminderMailConsumer consumer;
    @Autowired ReminderProperties properties;
    @Autowired PasswordEncoder encoder;
    @Autowired Environment environment;
    @Autowired ObjectMapper json;
    @MockitoBean Clock clock;
    @MockitoBean RabbitTemplate rabbit;
    @MockitoBean JavaMailSender sender;

    @BeforeEach void setup() { when(clock.instant()).thenReturn(NOW); acceptPublications(); }
    private void acceptPublications() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(eq(MailConfiguration.EXCHANGE), eq(MailConfiguration.REMINDER_ROUTING_KEY),
                any(DailyReminderRequested.class), any(MessagePostProcessor.class), any(CorrelationData.class));
    }
    private DailyReminderScheduler scheduler() { return new DailyReminderScheduler(properties, clock, reminders, preparation, publisher); }

    @Test void savesOnlyOwnOptInWithSessionCsrfValidationAndReload() throws Exception {
        var owner = account(false);
        var other = account(false);
        try (var client = new Client()) {
            assertEquals(401, client.get().statusCode());
            assertEquals(401, client.update(Map.of("enabled", true), true).statusCode());
            client.login(owner);
            assertFalse(json.readTree(client.get().body()).get("enabled").asBoolean());
            assertEquals(403, client.update(Map.of("enabled", true), false).statusCode());
            assertEquals(400, client.update(Map.of(), true).statusCode());
            var response = client.update(Map.of("enabled", true, "userId", other.getId()), true);
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            var body = json.readTree(response.body());
            assertTrue(body.get("enabled").asBoolean());
            assertEquals(18, body.get("sendHour").asInt());
            assertEquals("Europe/Warsaw", body.get("timezone").asText());
            assertTrue(json.readTree(client.get().body()).get("enabled").asBoolean());
            assertTrue(users.findById(owner.getId()).orElseThrow().isDailyRemindersEnabled());
            assertFalse(users.findById(other.getId()).orElseThrow().isDailyRemindersEnabled());
            assertEquals(200, client.update(Map.of("enabled", false), true).statusCode());
            assertFalse(users.findById(owner.getId()).orElseThrow().isDailyRemindersEnabled());
            verifyNoInteractions(rabbit, sender);
        }
    }

    @Test void schedulesAtMostOncePerDayAndDeduplicatesRepeatedDelivery() {
        account(true);
        when(clock.instant()).thenReturn(NOW.minus(1, ChronoUnit.HOURS));
        scheduler().tick();
        assertEquals(0, reminders.count());
        when(clock.instant()).thenReturn(NOW);
        scheduler().tick(); scheduler().tick();
        assertEquals(1, reminders.count());
        var dispatch = reminders.findAll().getFirst();
        assertEquals(DailyReminderDispatch.Status.PUBLISHED, dispatch.getStatus());
        consumer.send(new DailyReminderRequested(dispatch.getId()));
        consumer.send(new DailyReminderRequested(dispatch.getId()));
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
        assertEquals(DailyReminderDispatch.Status.SENT, reminders.findById(dispatch.getId()).orElseThrow().getStatus());
        assertNotNull(reminders.findById(dispatch.getId()).orElseThrow().getSentAt());
        when(clock.instant()).thenReturn(NOW.plus(1, ChronoUnit.DAYS));
        scheduler().tick();
        assertEquals(2, reminders.count());
    }

    @Test void skipsDisabledUnverifiedBlockedFutureOnlyAndAlreadyStudiedAccounts() {
        account(false);
        var blocked = account(true); blocked.setEnabled(false); users.saveAndFlush(blocked);
        var unverified = account(true); unverified.setEmailVerifiedAt(null); users.saveAndFlush(unverified);
        var future = account(true);
        var futureCard = cards.findByDeck_IdAndDeck_Owner_IdOrderByCreatedAtAscIdAsc(deckFor(future).getId(), future.getId()).getFirst();
        var state = new FlashcardProgress(future, futureCard);
        state.recordReview(true, 2, 0, NOW.minusSeconds(100), NOW.plus(1, ChronoUnit.DAYS)); progress.saveAndFlush(state);
        var studied = account(true); answerToday(studied);
        var eligible = account(true);
        scheduler().tick();
        assertEquals(1, reminders.count());
        assertEquals(eligible.getId(), reminders.findAll().getFirst().getUser().getId());
    }

    @Test void rechecksOptInActivityAndCurrentAddressAtDeliveryTime() {
        var disabled = account(true);
        var active = account(true);
        var learned = account(true);
        scheduler().tick();
        disabled.setDailyRemindersEnabled(false); users.saveAndFlush(disabled);
        active.setEmail("changed@example.invalid"); users.saveAndFlush(active);
        answerToday(learned);
        for (var dispatch : reminders.findAll()) consumer.send(new DailyReminderRequested(dispatch.getId()));
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(1)).send(message.capture());
        assertArrayEquals(new String[]{"changed@example.invalid"}, message.getValue().getTo());
        assertTrue(message.getValue().getText().contains("http://localhost:3000/dashboard"));
        assertTrue(message.getValue().getText().contains("http://localhost:3000/settings"));
        assertEquals(2, reminders.findAll().stream().filter(item -> item.getStatus() == DailyReminderDispatch.Status.SKIPPED).count());
    }

    @Test void publicationFailureLeavesDurablePendingDispatchAndNextTickRetriesIt() {
        account(true);
        doThrow(new IllegalStateException("Test publication failure")).when(rabbit).convertAndSend(anyString(), anyString(),
                any(DailyReminderRequested.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        scheduler().tick();
        assertEquals(1, reminders.count());
        assertEquals(DailyReminderDispatch.Status.PENDING, reminders.findAll().getFirst().getStatus());
        acceptPublications();
        scheduler().tick();
        assertEquals(1, reminders.count());
        assertEquals(DailyReminderDispatch.Status.PUBLISHED, reminders.findAll().getFirst().getStatus());
        verifyNoInteractions(sender);
    }

    @Test void ignoresYesterdayAndDeletedAccountMessages() {
        var owner = account(true);
        scheduler().tick();
        Long id = reminders.findAll().getFirst().getId();
        when(clock.instant()).thenReturn(NOW.plus(1, ChronoUnit.DAYS));
        consumer.send(new DailyReminderRequested(id));
        assertEquals(DailyReminderDispatch.Status.SKIPPED, reminders.findById(id).orElseThrow().getStatus());
        users.deleteById(owner.getId()); users.flush();
        consumer.send(new DailyReminderRequested(id));
        assertFalse(reminders.existsById(id));
        verifyNoInteractions(sender);
    }

    private User account(boolean enabled) {
        String name = "Reminder" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(NOW.minusSeconds(3600)); user.setDailyRemindersEnabled(enabled); users.saveAndFlush(user);
        var deck = decks.saveAndFlush(new Deck(user, "Practice", DeckType.GENERAL, "English", null));
        cards.saveAndFlush(new Flashcard(deck, "Hello", "cześć"));
        return user;
    }
    private Deck deckFor(User user) { return decks.findAll().stream().filter(deck -> deck.getOwner().getId().equals(user.getId())).findFirst().orElseThrow(); }
    private void answerToday(User user) {
        var deck = deckFor(user);
        var card = cards.findByDeck_IdAndDeck_Owner_IdOrderByCreatedAtAscIdAsc(deck.getId(), user.getId()).getFirst();
        var session = sessions.saveAndFlush(new StudySession(user, deck, 1, NOW.minusSeconds(60), StudyMode.FREE_PRACTICE));
        var answer = new StudyAnswer(session, card, 1);
        answer.recordAnswer("cześć", true, false, NOW.minusSeconds(30)); answers.saveAndFlush(answer);
    }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        private String csrf() throws Exception {
            var response = http.send(HttpRequest.newBuilder(endpoint("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            var response = http.send(HttpRequest.newBuilder(endpoint("/api/auth/login")).header("Content-Type", "application/json").header("X-CSRF-TOKEN", csrf())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> get() throws Exception { return http.send(HttpRequest.newBuilder(endpoint("/api/users/me/reminders")).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        HttpResponse<String> update(Map<String, ?> body, boolean protectedRequest) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/users/me/reminders")).header("Content-Type", "application/json");
            if (protectedRequest) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
