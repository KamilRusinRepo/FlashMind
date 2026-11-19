package com.example.backend.flashcard.csv;

import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
import com.example.backend.flashcard.Flashcard;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
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
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlashcardCsvPreviewApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "CsvPreviewTests123!";
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DeckRepository decks;
    @Autowired FlashcardRepository cards;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;

    @Test void previewsMultipartWithFieldErrorsAndLeavesTheDatabaseUnchanged() throws Exception {
        var owner = account();
        var deck = decks.saveAndFlush(new Deck(owner, "Preview", DeckType.GENERAL, "English", null));
        var existing = cards.saveAndFlush(new Flashcard(deck, "Existing", "Answer"));
        try (var client = new Client()) {
            client.login(owner);
            var response = client.preview(deck.getId(), "cards.csv", "question,answer\nŻółw,turtle\nMissing,\n", true);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var body = json.readTree(response.body());
            assertEquals(2, body.get("totalRows").asInt()); assertEquals(1, body.get("validRows").asInt());
            assertEquals(1, body.get("invalidRows").asInt()); assertFalse(body.get("valid").asBoolean());
            assertEquals("Żółw", body.get("rows").get(0).get("data").get("question").asText());
            assertEquals(3, body.get("rows").get(1).get("rowNumber").asInt());
            assertTrue(body.get("rows").get(1).get("fieldErrors").has("answer"));
            var valid = client.preview(deck.getId(), "cards.CSV", "question,answer\nQ,A\n", true);
            assertTrue(json.readTree(valid.body()).get("valid").asBoolean());
            assertEquals(1, cards.count()); assertEquals("Existing", cards.findById(existing.getId()).orElseThrow().getQuestion());
            assertEquals(400, client.preview(deck.getId(), "cards.txt", "question,answer\nQ,A", true).statusCode());
        }
    }

    @Test void requiresSessionCsrfAndOwnershipAndEnforcesUploadLimits() throws Exception {
        var owner = account(); var viewer = account();
        var deck = new Deck(owner, "Public", DeckType.GENERAL, "English", null);
        deck.setPublic(true); deck = decks.saveAndFlush(deck);
        try (var client = new Client()) {
            String csv = "question,answer\nQ,A\n";
            assertEquals(401, client.preview(deck.getId(), "cards.csv", csv, true).statusCode());
            client.login(viewer);
            assertEquals(404, client.preview(deck.getId(), "cards.csv", csv, true).statusCode());
            client.login(owner);
            assertEquals(403, client.preview(deck.getId(), "cards.csv", csv, false).statusCode());
            assertEquals(404, client.preview(Long.MAX_VALUE, "cards.csv", csv, true).statusCode());
            assertEquals(400, client.preview(0L, "cards.csv", csv, true).statusCode());
            assertEquals(413, client.preview(deck.getId(), "cards.csv", "x".repeat(1024 * 1024 + 1), true).statusCode());
            assertEquals(0, cards.count());
        }
    }

    private User account() {
        String name = "CsvPreview" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name + "@example.invalid", encoder.encode(PASSWORD));
        user.addRole(roles.findByName(RoleName.USER).orElseThrow()); user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }
    private URI endpoint(String path) { return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path); }
    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        String csrf() throws Exception {
            var response = http.send(HttpRequest.newBuilder(endpoint("/api/auth/csrf")).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode()); return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json").header("X-CSRF-TOKEN", csrf())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString()); assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> preview(Long deckId, String filename, String csv, boolean withCsrf) throws Exception {
            String boundary = "FlashMindCsvBoundary";
            var body = new ByteArrayOutputStream();
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\nContent-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(csv.getBytes(StandardCharsets.UTF_8)); body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(endpoint("/api/decks/" + deckId + "/flashcards/import/preview")).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary).header("Accept", "application/json");
            if (withCsrf) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
