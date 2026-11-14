package com.example.backend.study;

import java.time.Instant;
import java.util.Objects;
import com.example.backend.deck.Deck;
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
@Table(name = "study_sessions")
public class StudySession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false, updatable = false)
    private Deck deck;

    @Column(name = "total_cards", nullable = false, updatable = false)
    private int totalCards;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private StudyMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudySessionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected StudySession() {}

    public StudySession(User user, Deck deck, int totalCards, Instant startedAt) {
        this(user, deck, totalCards, startedAt, StudyMode.SCHEDULED_REVIEW);
    }

    public StudySession(User user, Deck deck, int totalCards, Instant startedAt, StudyMode mode) {
        if (totalCards < 1) throw new IllegalArgumentException("A study session must contain at least one flashcard.");
        this.user = Objects.requireNonNull(user);
        this.deck = Objects.requireNonNull(deck);
        this.totalCards = totalCards;
        this.mode = Objects.requireNonNull(mode);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.status = StudySessionStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Deck getDeck() { return deck; }
    public int getTotalCards() { return totalCards; }
    public StudyMode getMode() { return mode; }
    public StudySessionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public boolean isActive() { return status == StudySessionStatus.ACTIVE; }

    public void complete(Instant endedAt) { end(StudySessionStatus.COMPLETED, endedAt); }
    public void abandon(Instant endedAt) { end(StudySessionStatus.ABANDONED, endedAt); }

    private void end(StudySessionStatus status, Instant endedAt) {
        if (!isActive()) throw new IllegalStateException("This study session has already ended.");
        var end = Objects.requireNonNull(endedAt);
        if (end.isBefore(startedAt)) throw new IllegalArgumentException("A session cannot end before it starts.");
        this.status = status;
        this.endedAt = end;
    }
}
