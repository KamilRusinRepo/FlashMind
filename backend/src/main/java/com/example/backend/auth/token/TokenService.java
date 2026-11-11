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
        Objects.requireNonNull(type);
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot issue a token for a missing user."));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(switch (type) {
            case EMAIL_VERIFICATION -> properties.emailVerificationTtl();
            case PASSWORD_RESET -> properties.passwordResetTtl();
        });
        tokens.saveAndFlush(new UserToken(user, hash(value), type, createdAt, expiresAt));
        return new IssuedToken(value, expiresAt);
    }

    /**
     * Future verification/reset services must call this inside their own transaction,
     * so token consumption and the corresponding account change commit or roll back together.
     */
    @Transactional
    public User consume(String value, TokenType expectedType) {
        Objects.requireNonNull(expectedType);
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
            throw new InvalidTokenException();
        }
        UserToken token = tokens.findByTokenHashForUpdate(hash(value)).orElseThrow(InvalidTokenException::new);
        if (token.getType() != expectedType) {
            throw new InvalidTokenException();
        }
        token.markUsed(clock.instant());
        return token.getUser();
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
