package com.example.backend.admin;

import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
import com.example.backend.deck.review.DeckReview;
import com.example.backend.deck.review.DeckReviewRepository;
import com.example.backend.flashcard.Flashcard;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import com.example.backend.user.UserDeletionService;
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
class AdminReviewApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "AdminReviewTests123!";
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DeckRepository decks;
    @Autowired DeckReviewRepository reviews;
    @Autowired FlashcardRepository cards;
    @Autowired UserDeletionService deletion;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test void listsNewestReviewsWithPaginationIncludingPrivateAndAuthorlessDecks() throws Exception {
        var admin = account(true); var owner = account(false); var reviewer = account(false); var removed = account(false);
        var shared = deck(owner, true); var hidden = deck(owner, false); var orphan = deck(removed, true);
        var first = reviews.saveAndFlush(new DeckReview(reviewer, shared, 4, "<b>Plain text</b>"));
        var second = reviews.saveAndFlush(new DeckReview(admin, hidden, 2, null));
        var third = reviews.saveAndFlush(new DeckReview(reviewer, orphan, 5, "Preserved"));
        deletion.deleteAccount(removed.getId());
        try (var client = new Client()) {
            client.login(admin);
            var response = client.get("/api/admin/reviews?size=2");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var data = json.readTree(response.body());
            assertEquals(3, data.get("totalElements").asInt()); assertEquals(2, data.get("totalPages").asInt());
            assertEquals(2, data.get("content").size());
            assertEquals(third.getId().longValue(), data.get("content").get(0).get("id").asLong());
            assertEquals(orphan.getId().longValue(), data.get("content").get(0).get("deck").get("id").asLong());
            assertEquals(reviewer.getUsername(), data.get("content").get(0).get("author").get("displayName").asText());
            assertEquals(second.getId().longValue(), data.get("content").get(1).get("id").asLong());
            assertFalse(data.get("content").get(1).get("deck").get("publicDeck").asBoolean());
            assertFalse(response.body().contains(reviewer.getEmail())); assertFalse(response.body().contains("password"));
            var next = json.readTree(client.get("/api/admin/reviews?size=2&page=1").body());
            assertEquals(1, next.get("content").size());
            assertEquals(first.getId().longValue(), next.get("content").get(0).get("id").asLong());
            assertEquals("<b>Plain text</b>", next.get("content").get(0).get("comment").asText());
            assertEquals(0, json.readTree(client.get("/api/admin/reviews?page=99").body()).get("content").size());
            for (String query : new String[]{"?page=-1", "?page=1000001", "?page=abc", "?size=0", "?size=51"})
                assertEquals(400, client.get("/api/admin/reviews" + query).statusCode(), query);
        }
    }

    @Test void deletesAnyReviewAndRefreshesAggregatesWithoutDeletingDeckOrCards() throws Exception {
        var admin = account(true); var owner = account(false); var reviewer = account(false);
        var shared = deck(owner, true); var hidden = deck(owner, false);
        var card = cards.saveAndFlush(new Flashcard(shared, "Question", "Answer"));
        var high = reviews.saveAndFlush(new DeckReview(reviewer, shared, 5, "Delete this"));
        var low = reviews.saveAndFlush(new DeckReview(admin, shared, 1, null));
        var privateReview = reviews.saveAndFlush(new DeckReview(reviewer, hidden, 2, "Private"));
        try (var client = new Client()) {
            client.login(admin);
            var response = client.delete(high.getId(), true);
            assertEquals(204, response.statusCode(), response.body()); assertEquals("", response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(reviews.existsById(high.getId()));
            var summary = json.readTree(client.get("/api/explore/decks/" + shared.getId() + "/reviews").body()).get("summary");
            assertEquals(1, summary.get("reviewCount").asInt()); assertEquals(1.0, summary.get("averageRating").asDouble());
            var detail = json.readTree(client.get("/api/explore/decks/" + shared.getId()).body());
            assertEquals(1, detail.get("reviewCount").asInt()); assertEquals(1.0, detail.get("averageRating").asDouble());
            assertEquals(404, client.delete(high.getId(), true).statusCode());
            assertEquals(204, client.delete(privateReview.getId(), true).statusCode());
            assertEquals(204, client.delete(low.getId(), true).statusCode());
            summary = json.readTree(client.get("/api/explore/decks/" + shared.getId() + "/reviews").body()).get("summary");
            assertEquals(0, summary.get("reviewCount").asInt()); assertTrue(summary.get("averageRating").isNull());
            assertEquals(0, json.readTree(client.get("/api/admin/reviews").body()).get("totalElements").asInt());
            assertTrue(decks.existsById(shared.getId())); assertTrue(decks.existsById(hidden.getId()));
            assertTrue(cards.existsById(card.getId())); assertTrue(users.existsById(reviewer.getId()));
        }
    }

    @Test void requiresAdminCsrfAndCurrentPrivilegesForModeration() throws Exception {
        var admin = account(true); var reviewer = account(false); var owner = account(false);
        var deck = deck(owner, true); var review = reviews.saveAndFlush(new DeckReview(reviewer, deck, 3, "Keep"));
        try (var client = new Client()) {
            assertEquals(401, client.get("/api/admin/reviews").statusCode());
            assertEquals(401, client.delete(review.getId(), true).statusCode());
            client.login(reviewer);
            assertEquals(403, client.get("/api/admin/reviews").statusCode());
            assertEquals(403, client.delete(review.getId(), true).statusCode());
            client.login(owner);
            assertEquals(403, client.delete(review.getId(), true).statusCode());
            client.login(admin);
            assertEquals(403, client.delete(review.getId(), false).statusCode());
            assertEquals(400, client.delete(0L, true).statusCode());
            assertEquals(404, client.delete(Long.MAX_VALUE, true).statusCode());
            admin.removeRole(admin.getRoles().stream().filter(role -> role.getName() == RoleName.ADMIN).findFirst().orElseThrow());
            admin.invalidateSessions(); users.saveAndFlush(admin);
            assertEquals(401, client.get("/api/admin/reviews").statusCode());
            client.login(admin);
            assertEquals(403, client.delete(review.getId(), true).statusCode());
            assertTrue(reviews.existsById(review.getId()));
        }
    }

    private User account(boolean admin) {
        String name = "AdminReview" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        if (admin) user.addRole(roles.findByName(RoleName.ADMIN).orElseThrow());
        user.setEmailVerifiedAt(Instant.now()); return users.saveAndFlush(user);
    }
    private Deck deck(User owner, boolean visible) {
        var deck = new Deck(owner, "Moderated deck", DeckType.GENERAL, "English", null);
        deck.setPublic(visible); return decks.saveAndFlush(deck);
    }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        String csrf() throws Exception {
            var response = get("/api/auth/csrf"); assertEquals(200, response.statusCode()); return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).timeout(Duration.ofSeconds(15)).header("X-CSRF-TOKEN", csrf()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString()); assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> delete(Long id, boolean withCsrf) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/admin/reviews/" + id)).timeout(Duration.ofSeconds(15)).DELETE();
            if (withCsrf) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
