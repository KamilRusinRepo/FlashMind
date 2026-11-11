package com.example.backend.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TokenServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final String RAW_TOKEN = "A".repeat(43);
    private final UserTokenRepository tokens = mock(UserTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final User user = mock(User.class);
    private final TokenService service = new TokenService(tokens, users,
            new TokenProperties(Duration.ofHours(24), Duration.ofMinutes(30)), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void issuesRandomVerificationTokenAndStoresOnlyItsHash() throws Exception {
        when(users.findById(1L)).thenReturn(Optional.of(user));

        var issued = service.issue(1L, TokenType.EMAIL_VERIFICATION);

        var captor = ArgumentCaptor.forClass(UserToken.class);
        verify(tokens).saveAndFlush(captor.capture());
        UserToken stored = captor.getValue();
        assertTrue(issued.value().matches("[A-Za-z0-9_-]{43}"));
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(issued.value().getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedHash, stored.getTokenHash());
        assertNotEquals(issued.value(), stored.getTokenHash());
        assertEquals(NOW, stored.getCreatedAt());
        assertEquals(NOW.plus(Duration.ofHours(24)), issued.expiresAt());
        assertEquals(issued.expiresAt(), stored.getExpiresAt());
        assertNull(stored.getUsedAt());
        assertSame(user, stored.getUser());
        assertFalse(issued.toString().contains(issued.value()));
    }

    @Test
    void usesSeparateLifetimeForPasswordReset() {
        when(users.findById(1L)).thenReturn(Optional.of(user));

        var issued = service.issue(1L, TokenType.PASSWORD_RESET);

        assertEquals(NOW.plus(Duration.ofMinutes(30)), issued.expiresAt());
    }

    @Test
    void successiveTokensHaveDifferentValues() {
        when(users.findById(1L)).thenReturn(Optional.of(user));

        assertNotEquals(service.issue(1L, TokenType.EMAIL_VERIFICATION).value(),
                service.issue(1L, TokenType.EMAIL_VERIFICATION).value());
    }

    @Test
    void validTokenIsMarkedUsedAndReturnsItsOwner() {
        UserToken token = token(TokenType.EMAIL_VERIFICATION, NOW.plusSeconds(60));
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertSame(user, service.consume(RAW_TOKEN, TokenType.EMAIL_VERIFICATION));
        assertEquals(NOW, token.getUsedAt());
    }

    @Test
    void expiryBoundaryIsAlreadyExpired() {
        UserToken token = token(TokenType.EMAIL_VERIFICATION, NOW);
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> service.consume(RAW_TOKEN, TokenType.EMAIL_VERIFICATION));
        assertNull(token.getUsedAt());
    }

    @Test
    void wrongPurposeDoesNotConsumeToken() {
        UserToken token = token(TokenType.PASSWORD_RESET, NOW.plusSeconds(60));
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> service.consume(RAW_TOKEN, TokenType.EMAIL_VERIFICATION));
        assertNull(token.getUsedAt());
    }

    @Test
    void previouslyUsedTokenCannotBeUsedAgain() {
        UserToken token = token(TokenType.EMAIL_VERIFICATION, NOW.plusSeconds(60));
        token.markUsed(NOW.minusSeconds(1));
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> service.consume(RAW_TOKEN, TokenType.EMAIL_VERIFICATION));
        assertEquals(NOW.minusSeconds(1), token.getUsedAt());
    }

    @Test
    void malformedTokensAreRejectedBeforeDatabaseLookup() {
        assertThrows(InvalidTokenException.class, () -> service.consume(null, TokenType.EMAIL_VERIFICATION));
        assertThrows(InvalidTokenException.class, () -> service.consume("", TokenType.EMAIL_VERIFICATION));
        assertThrows(InvalidTokenException.class, () -> service.consume("invalid-token", TokenType.EMAIL_VERIFICATION));
        verifyNoInteractions(tokens);
    }

    @Test
    void unknownTokenIsRejectedWithoutIncludingItsValueInMessage() {
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        var exception = assertThrows(InvalidTokenException.class,
                () -> service.consume(RAW_TOKEN, TokenType.EMAIL_VERIFICATION));

        assertFalse(exception.getMessage().contains(RAW_TOKEN));
    }

    private UserToken token(TokenType type, Instant expiresAt) {
        return new UserToken(user, "0".repeat(64), type, NOW.minusSeconds(60), expiresAt);
    }
}
