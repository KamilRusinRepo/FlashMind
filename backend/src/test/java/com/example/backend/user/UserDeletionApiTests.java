package com.example.backend.user;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
import com.example.backend.deck.dto.response.DeckAuthorResponse;
import com.example.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserDeletionApiTests extends PostgresIntegrationTest {
    private static final String PASSWORD = "DeletionTest123!";
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired TokenService tokens;
    @Autowired UserTokenRepository storedTokens;
    @Autowired UserDeletionService deletion;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthenticationManager authentication;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Environment environment;
    @Autowired ObjectMapper json;

    @Test
    void deletesOnlyCurrentAccountAndRetainsPublicDeckWithDeletedAuthor() throws Exception {
        var owner = account();
        var other = account();
        var publicDeck = new Deck(owner, "Shared learning", DeckType.GENERAL, "English", null);
        publicDeck.setPublic(true);
        Long publicId = decks.saveAndFlush(publicDeck).getId();
        Long privateId = decks.saveAndFlush(new Deck(owner, "Private notes", DeckType.GENERAL, "English", null)).getId();
        var otherToken = tokens.issue(other.getId(), TokenType.PASSWORD_RESET);
        tokens.issue(owner.getId(), TokenType.PASSWORD_RESET);
        tokens.issue(owner.getId(), TokenType.EMAIL_VERIFICATION);

        try (var client = new Client(); var otherSession = new Client()) {
            client.login(owner);
            otherSession.login(owner);
            assertEquals(204, client.delete(true));
            assertEquals(401, client.me());
            assertEquals(401, otherSession.me());
        }

        assertFalse(users.existsById(owner.getId()));
        assertFalse(users.existsByUsernameIgnoreCase(owner.getUsername()));
        assertFalse(users.existsByEmailIgnoreCase(owner.getEmail()));
        assertEquals("Deleted user", DeckAuthorResponse.from(null).displayName());
        assertNull(DeckAuthorResponse.from(null).id());
        assertThrows(BadCredentialsException.class, () -> authentication.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(owner.getUsername(), PASSWORD)));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var retained = decks.findById(publicId).orElseThrow();
            assertTrue(retained.isPublic());
            assertNull(retained.getOwner());
            assertEquals("Deleted user", DeckAuthorResponse.from(retained.getOwner()).displayName());
            assertFalse(decks.existsById(privateId));
        });
        assertTrue(users.findById(other.getId()).isPresent());
        assertEquals(other.getUsername(), DeckAuthorResponse.from(other).displayName());
        assertEquals(1, storedTokens.count());
        assertEquals(other.getId(), tokens.consume(otherToken.value(), TokenType.PASSWORD_RESET).getId());

        deletion.deleteAccount(owner.getId());
        assertFalse(users.existsById(owner.getId()));
        assertTrue(decks.existsById(publicId));
    }

    @Test
    void deletionRequiresAuthenticationAndCsrf() throws Exception {
        var owner = account();
        try (var anonymous = new Client(); var client = new Client()) {
            assertEquals(401, anonymous.delete(true));
            client.login(owner);
            assertEquals(403, client.delete(false));
            assertTrue(users.existsById(owner.getId()));
        }
    }

    private User account() {
        String name = "Delete" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var user = new User(name, name.toLowerCase() + "@example.invalid", encoder.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private URI endpoint(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }

    private class Client implements AutoCloseable {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String csrf() throws Exception {
            var response = http.send(HttpRequest.newBuilder(endpoint("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            return json.readTree(response.body()).get("token").asText();
        }
        void login(User user) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/auth/login")).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", csrf()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            Map.of("login", user.getUsername(), "password", PASSWORD)))).build();
            assertEquals(200, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        int me() throws Exception {
            return http.send(HttpRequest.newBuilder(endpoint("/api/auth/me")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        }
        int delete(boolean withCsrf) throws Exception {
            var request = HttpRequest.newBuilder(endpoint("/api/users/me"));
            if (withCsrf) request.header("X-CSRF-TOKEN", csrf());
            return http.send(request.DELETE().build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        }
        @Override public void close() { http.close(); }
    }
}
