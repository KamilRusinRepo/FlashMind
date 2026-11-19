package com.example.backend.deck.review;

import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeckReviewApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "ReviewReadTests123!";
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DeckRepository decks;
    @Autowired DeckReviewRepository reviews;
    @Autowired UserDeletionService deletion;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test void listsNewestFirstWithPaginationSafeAuthorsAndOverallStatisticsAcrossExploreResponses() throws Exception {
        var owner = account(); var viewer = account(); var deck = deck(owner, true);
        var own = reviews.saveAndFlush(new DeckReview(viewer, deck, 5, "Zażółć <b>tekst</b>"));
        DeckReview latest = own;
        for (int rating : new int[]{4, 3, 5, 4, 2, 1}) latest = reviews.saveAndFlush(new DeckReview(account(), deck, rating, null));
        try (var client = new Client()) {
            client.login(viewer);
            var response = client.get("/api/explore/decks/" + deck.getId() + "/reviews?size=3");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var data = json.readTree(response.body());
            assertEquals(7, data.get("totalElements").asInt()); assertEquals(3, data.get("totalPages").asInt());
            assertEquals(3, data.get("content").size());
            assertEquals(own.getId().longValue(), data.get("ownReview").get("id").asLong());
            assertEquals(latest.getId().longValue(), data.get("content").get(0).get("id").asLong());
            assertTrue(data.get("content").get(0).get("comment").isNull());
            var summary = data.get("summary");
            assertEquals(7, summary.get("reviewCount").asInt()); assertEquals(24.0 / 7, summary.get("averageRating").asDouble(), 1e-9);
            assertEquals(5, summary.get("distribution").size());
            assertEquals(5, summary.get("distribution").get(0).get("rating").asInt());
            assertEquals(2, summary.get("distribution").get(0).get("count").asInt());
            assertFalse(response.body().contains("password")); assertFalse(response.body().contains("email"));
            var last = client.get("/api/explore/decks/" + deck.getId() + "/reviews?size=3&page=2");
            var row = json.readTree(last.body()).get("content").get(0);
            assertEquals(own.getId().longValue(), row.get("id").asLong()); assertTrue(row.get("ownReview").asBoolean());
            assertEquals(viewer.getUsername(), row.get("author").get("displayName").asText());
            assertEquals("Zażółć <b>tekst</b>", row.get("comment").asText());
            var details = json.readTree(client.get("/api/explore/decks/" + deck.getId()).body());
            assertEquals(7, details.get("reviewCount").asInt()); assertEquals(24.0 / 7, details.get("averageRating").asDouble(), 1e-9);
            var listed = json.readTree(client.get("/api/explore/decks").body()).get("content").get(0);
            assertEquals(deck.getId().longValue(), listed.get("id").asLong());
            assertEquals(7, listed.get("reviewCount").asInt()); assertEquals(24.0 / 7, listed.get("averageRating").asDouble(), 1e-9);
            var outside = json.readTree(client.get("/api/explore/decks/" + deck.getId() + "/reviews?page=99").body());
            assertEquals(0, outside.get("content").size()); assertEquals(7, outside.get("totalElements").asInt());
        }
    }

    @Test void handlesZeroReviewsAndRequiresAuthenticationPublicVisibilityAndValidPagination() throws Exception {
        var owner = account(); var deck = deck(owner, true); var hidden = deck(owner, false);
        try (var client = new Client()) {
            String path = "/api/explore/decks/" + deck.getId() + "/reviews";
            assertEquals(401, client.get(path).statusCode()); client.login(owner);
            var response = client.get(path); assertEquals(200, response.statusCode(), response.body());
            var data = json.readTree(response.body());
            assertEquals(0, data.get("totalElements").asInt()); assertEquals(0, data.get("totalPages").asInt());
            assertTrue(data.get("summary").get("averageRating").isNull());
            for (var bucket : data.get("summary").get("distribution")) assertEquals(0, bucket.get("count").asInt());
            var details = json.readTree(client.get("/api/explore/decks/" + deck.getId()).body());
            assertEquals(0, details.get("reviewCount").asInt()); assertTrue(details.get("averageRating").isNull());
            assertEquals(404, client.get("/api/explore/decks/" + hidden.getId() + "/reviews").statusCode());
            assertEquals(404, client.get("/api/explore/decks/" + Long.MAX_VALUE + "/reviews").statusCode());
            assertEquals(400, client.get("/api/explore/decks/0/reviews").statusCode());
            for (String query : new String[]{"?page=-1", "?page=1000001", "?size=0", "?size=51", "?page=abc"})
                assertEquals(400, client.get(path + query).statusCode(), query);
        }
    }

    @Test void keepsOtherUsersReviewsAfterDeletingDeckOwnerAndRecalculatesAfterDeletingReviewer() throws Exception {
        var owner = account(); var viewer = account(); var reviewer = account(); var deck = deck(owner, true);
        reviews.saveAndFlush(new DeckReview(viewer, deck, 5, "Retained"));
        reviews.saveAndFlush(new DeckReview(reviewer, deck, 1, null));
        deletion.deleteAccount(owner.getId());
        try (var client = new Client()) {
            client.login(viewer);
            String path = "/api/explore/decks/" + deck.getId();
            var initial = json.readTree(client.get(path + "/reviews").body());
            assertEquals(2, initial.get("totalElements").asInt()); assertEquals(3.0, initial.get("summary").get("averageRating").asDouble());
            assertEquals("Deleted user", json.readTree(client.get(path).body()).get("author").get("displayName").asText());
            deletion.deleteAccount(reviewer.getId());
            var remaining = json.readTree(client.get(path + "/reviews").body());
            assertEquals(1, remaining.get("totalElements").asInt()); assertEquals(5.0, remaining.get("summary").get("averageRating").asDouble());
            assertEquals(1, json.readTree(client.get(path).body()).get("reviewCount").asInt());
            deck = decks.findById(deck.getId()).orElseThrow(); deck.setPublic(false); decks.saveAndFlush(deck);
            assertEquals(404, client.get(path + "/reviews").statusCode());
        }
    }

    @Test void onlyOwnerCanReadRetainedReviewsAfterMakingDeckPrivate() throws Exception {
        var owner = account(); var viewer = account(); var deck = deck(owner, true);
        reviews.saveAndFlush(new DeckReview(viewer, deck, 4, "Keep this review"));
        deck.setPublic(false); decks.saveAndFlush(deck);
        String owned = "/api/decks/" + deck.getId() + "/reviews";
        String explored = "/api/explore/decks/" + deck.getId() + "/reviews";
        try (var client = new Client()) {
            assertEquals(401, client.get(owned).statusCode());
            client.login(viewer);
            assertEquals(404, client.get(owned).statusCode()); assertEquals(404, client.get(explored).statusCode());
            client.login(owner);
            var response = client.get(owned);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var data = json.readTree(response.body());
            assertEquals(1, data.get("totalElements").asInt()); assertEquals(4.0, data.get("summary").get("averageRating").asDouble());
            assertEquals("Keep this review", data.get("content").get(0).get("comment").asText());
            assertEquals(404, client.get(explored).statusCode());
            var empty = deck(owner, false);
            assertEquals(0, json.readTree(client.get("/api/decks/" + empty.getId() + "/reviews").body()).get("totalElements").asInt());
            assertEquals(404, client.get("/api/decks/" + Long.MAX_VALUE + "/reviews").statusCode());
            assertEquals(400, client.get(owned + "?size=0").statusCode());
        }
    }

    @Test void createsReviewWithCurrentAuthorAndNormalizesCommentIncludingAuthorlessPublicDecks() throws Exception {
        var owner = account(); var viewer = account(); var deck = deck(owner, true);
        try (var client = new Client()) {
            client.login(viewer);
            var response = client.postReview(deck.getId(), "{\"rating\":5,\"comment\":\"  Przydatna talia!  \"}", true);
            assertEquals(201, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var data = json.readTree(response.body());
            assertEquals(viewer.getId().longValue(), data.get("author").get("id").asLong());
            assertEquals(viewer.getUsername(), data.get("author").get("displayName").asText());
            assertEquals(5, data.get("rating").asInt()); assertEquals("Przydatna talia!", data.get("comment").asText());
            assertTrue(data.get("ownReview").asBoolean()); assertFalse(response.body().contains("email"));
            var page = json.readTree(client.get("/api/explore/decks/" + deck.getId() + "/reviews").body());
            assertEquals(1, page.get("summary").get("reviewCount").asInt());
            assertEquals(data.get("id").asLong(), page.get("ownReview").get("id").asLong());
            deletion.deleteAccount(owner.getId());
            var removed = account(); var orphan = deck(removed, true); deletion.deleteAccount(removed.getId());
            var noComment = client.postReview(orphan.getId(), "{\"rating\":1,\"comment\":\"   \"}", true);
            assertEquals(201, noComment.statusCode(), noComment.body()); assertTrue(json.readTree(noComment.body()).get("comment").isNull());
            assertEquals(2, reviews.count());
        }
    }

    @Test void rejectsOwnPrivateDuplicateInvalidAndUnprotectedReviewRequests() throws Exception {
        var owner = account(); var viewer = account(); var deck = deck(owner, true); var hidden = deck(owner, false);
        String valid = "{\"rating\":4}";
        try (var client = new Client()) {
            assertEquals(401, client.postReview(deck.getId(), valid, true).statusCode());
            client.login(owner);
            assertEquals(409, client.postReview(deck.getId(), valid, true).statusCode());
            client.login(viewer);
            assertEquals(403, client.postReview(deck.getId(), valid, false).statusCode());
            assertEquals(404, client.postReview(hidden.getId(), valid, true).statusCode());
            for (String body : new String[]{"{}", "{\"rating\":null}", "{\"rating\":0}", "{\"rating\":6}", "{\"rating\":4.5}"}) {
                var response = client.postReview(deck.getId(), body, true);
                assertEquals(400, response.statusCode(), response.body()); assertTrue(json.readTree(response.body()).get("fieldErrors").has("rating"));
            }
            var tooLong = client.postReview(deck.getId(), json.writeValueAsString(Map.of("rating", 4, "comment", "x".repeat(1001))), true);
            assertEquals(400, tooLong.statusCode()); assertTrue(json.readTree(tooLong.body()).get("fieldErrors").has("comment"));
            assertEquals(0, reviews.count());
            assertEquals(201, client.postReview(deck.getId(), valid, true).statusCode());
            var duplicate = client.postReview(deck.getId(), "{\"rating\":1}", true);
            assertEquals(409, duplicate.statusCode());
            assertEquals("You have already reviewed this deck.", json.readTree(duplicate.body()).get("message").asText());
            assertEquals(4, reviews.findByUser_IdAndDeck_Id(viewer.getId(), deck.getId()).orElseThrow().getRating());
            var changed = deck(owner, true);
            assertEquals(200, client.get("/api/explore/decks/" + changed.getId()).statusCode());
            changed.setPublic(false); decks.saveAndFlush(changed);
            assertEquals(404, client.postReview(changed.getId(), valid, true).statusCode());
            assertEquals(404, client.postReview(Long.MAX_VALUE, valid, true).statusCode());
            assertEquals(400, client.postReview(0L, valid, true).statusCode());
            assertEquals(1, reviews.count());
        }
    }

    @Test void simultaneousSubmissionsFromTwoSessionsCreateOnlyOneReview() throws Exception {
        var owner = account(); var viewer = account(); var deck = deck(owner, true);
        try (var first = new Client(); var second = new Client()) {
            first.login(viewer); second.login(viewer);
            String firstToken = first.csrf(); String secondToken = second.csrf();
            var one = CompletableFuture.supplyAsync(() -> createStatus(first, deck.getId(), firstToken));
            var two = CompletableFuture.supplyAsync(() -> createStatus(second, deck.getId(), secondToken));
            int a = one.join(); int b = two.join();
            assertTrue(a == 201 && b == 409 || a == 409 && b == 201, "Statuses: " + a + ", " + b);
            assertEquals(1, reviews.count());
        }
    }

    private int createStatus(Client client, Long deckId, String token) {
        try { return client.postReviewWithToken(deckId, "{\"rating\":5}", token).statusCode(); }
        catch (Exception exception) { throw new CompletionException(exception); }
    }

    private User account() {
        String name = "ReviewRead" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.addRole(roles.findByName(RoleName.USER).orElseThrow()); user.setEmailVerifiedAt(Instant.now()); return users.saveAndFlush(user);
    }
    private Deck deck(User owner, boolean visible) {
        var deck = new Deck(owner, "Review deck", DeckType.GENERAL, "English", null);
        deck.setPublic(visible); return decks.saveAndFlush(deck);
    }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint(path)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        void login(User user) throws Exception {
            String csrf = json.readTree(get("/api/auth/csrf").body()).get("token").asText();
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json").header("X-CSRF-TOKEN", csrf)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString()); assertEquals(200, response.statusCode(), response.body());
        }
        String csrf() throws Exception {
            var response = get("/api/auth/csrf"); assertEquals(200, response.statusCode()); return json.readTree(response.body()).get("token").asText();
        }
        HttpResponse<String> postReview(Long deckId, String body, boolean withCsrf) throws Exception {
            return postReviewWithToken(deckId, body, withCsrf ? csrf() : null);
        }
        HttpResponse<String> postReviewWithToken(Long deckId, String body, String token) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/explore/decks/" + deckId + "/reviews")).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json");
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
