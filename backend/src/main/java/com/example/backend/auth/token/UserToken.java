package com.example.backend.auth.token;

import java.time.Instant;
import java.util.Objects;

import com.example.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_tokens")
public class UserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private TokenType type;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "target_email", length = 255, updatable = false)
    private String targetEmail;

    protected UserToken() {
    }

    public UserToken(User user, String tokenHash, TokenType type, Instant createdAt, Instant expiresAt) {
        this(user, tokenHash, type, createdAt, expiresAt, null);
    }

    public UserToken(User user, String tokenHash, TokenType type, Instant createdAt, Instant expiresAt, String targetEmail) {
        if (type == TokenType.EMAIL_CHANGE ? targetEmail == null || targetEmail.isBlank() : targetEmail != null) {
            throw new IllegalArgumentException("Only email change tokens require a target address.");
        }
        this.targetEmail = targetEmail;
        this.user = Objects.requireNonNull(user);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.type = Objects.requireNonNull(type);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Token expiry must be after its creation time.");
        }
    }

    public Long getId() { return id; }
    public String getTargetEmail() { return targetEmail; }

    public User getUser() { return user; }

    public String getTokenHash() { return tokenHash; }

    public TokenType getType() { return type; }

    public Instant getExpiresAt() { return expiresAt; }

    public Instant getUsedAt() { return usedAt; }

    public Instant getCreatedAt() { return createdAt; }

    public void markUsed(Instant now) {
        Objects.requireNonNull(now);
        if (usedAt != null || now.isBefore(createdAt) || !expiresAt.isAfter(now)) {
            throw new InvalidTokenException();
        }
        usedAt = now;
    }
}
