package com.example.backend.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private final UserTokenRepository tokens;
    private final UserRepository users;
    private final TokenProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenService(UserTokenRepository tokens, UserRepository users, TokenProperties properties, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedToken issue(Long userId, TokenType type) {
        return issueToken(userId, type, null);
    }

    @Transactional
    public IssuedToken issueEmailChange(Long userId, String targetEmail) {
        return issueToken(userId, TokenType.EMAIL_CHANGE, Objects.requireNonNull(targetEmail));
    }

    private IssuedToken issueToken(Long userId, TokenType type, String targetEmail) {
        Objects.requireNonNull(type);
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot issue a token for a missing user."));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(switch (type) {
            case EMAIL_VERIFICATION, EMAIL_CHANGE -> properties.emailVerificationTtl();
            case PASSWORD_RESET -> properties.passwordResetTtl();
        });
        tokens.saveAndFlush(new UserToken(user, hash(value), type, createdAt, expiresAt, targetEmail));
        return new IssuedToken(value, expiresAt);
    }

    /**
     * Future verification/reset services must call this inside their own transaction,
     * so token consumption and the corresponding account change commit or roll back together.
     */
    @Transactional
    public User consume(String value, TokenType expectedType) {
        return consumeToken(value, expectedType).getUser();
    }

    @Transactional
    public UserToken consumeEmailChange(String value) {
        return consumeToken(value, TokenType.EMAIL_CHANGE);
    }

    private UserToken consumeToken(String value, TokenType expectedType) {
        Objects.requireNonNull(expectedType);
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
            throw new InvalidTokenException();
        }
        String tokenHash = hash(value);
        Long ownerId = tokens.findOwnerIdByTokenHash(tokenHash).orElseThrow(InvalidTokenException::new);
        // Resend and consumption lock in the same order: user, then token. This avoids deadlocks
        // and prevents a concurrently invalidated token from verifying the account.
        users.findByIdForUpdate(ownerId).orElseThrow(InvalidTokenException::new);
        UserToken token = tokens.findByTokenHashForUpdate(tokenHash).orElseThrow(InvalidTokenException::new);
        if (token.getType() != expectedType) {
            throw new InvalidTokenException();
        }
        token.markUsed(clock.instant());
        return token;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedToken[value=REDACTED, expiresAt=" + expiresAt + "]";
        }
    }
}
