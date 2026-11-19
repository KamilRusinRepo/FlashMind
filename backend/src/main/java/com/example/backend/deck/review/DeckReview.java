package com.example.backend.deck.review;

import java.time.Instant;
import java.util.Objects;
import com.example.backend.deck.Deck;
import com.example.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "deck_reviews", uniqueConstraints = @UniqueConstraint(name = "uq_deck_reviews_user_deck", columnNames = {"user_id", "deck_id"}))
public class DeckReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No cascade: saving/removing a review must never save/remove its user or deck.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false, updatable = false)
    private Deck deck;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeckReview() { }

    public DeckReview(User user, Deck deck, int rating, String comment) {
        this.user = Objects.requireNonNull(user);
        this.deck = Objects.requireNonNull(deck);
        update(rating, comment);
    }

    public void update(int rating, String comment) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be between 1 and 5.");
        String normalized = comment == null || comment.isBlank() ? null : comment.trim();
        if (normalized != null && normalized.length() > 1000) throw new IllegalArgumentException("Review comment must not exceed 1000 characters.");
        this.rating = rating;
        this.comment = normalized;
    }

    @PrePersist
    private void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void refreshUpdatedAt() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Deck getDeck() { return deck; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
