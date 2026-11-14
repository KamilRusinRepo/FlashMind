package com.example.backend.study;

import java.time.Instant;
import java.util.Objects;
import com.example.backend.flashcard.Flashcard;
import com.example.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "flashcard_progress", uniqueConstraints =
        @UniqueConstraint(name = "uq_flashcard_progress_user_card", columnNames = {"user_id", "flashcard_id"}))
public class FlashcardProgress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flashcard_id", nullable = false, updatable = false)
    private Flashcard flashcard;

    @Column(nullable = false)
    private int attempts;
    @Column(name = "correct_count", nullable = false)
    private int correctCount;
    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;
    @Column(name = "mastery_level", nullable = false)
    private int masteryLevel;
    @Column(name = "wrong_streak", nullable = false)
    private int wrongStreak;
    @Column(name = "last_answered_at")
    private Instant lastAnsweredAt;
    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected FlashcardProgress() {}

    public FlashcardProgress(User user, Flashcard flashcard) {
        this.user = Objects.requireNonNull(user);
        this.flashcard = Objects.requireNonNull(flashcard);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Flashcard getFlashcard() { return flashcard; }
    public int getAttempts() { return attempts; }
    public int getCorrectCount() { return correctCount; }
    public int getWrongCount() { return wrongCount; }
    public int getMasteryLevel() { return masteryLevel; }
    public int getWrongStreak() { return wrongStreak; }
    public Instant getLastAnsweredAt() { return lastAnsweredAt; }
    public Instant getNextReviewAt() { return nextReviewAt; }
    public long getVersion() { return version; }

    // StudyService supplies the schedule calculated by StudyRules.
    public void recordReview(boolean correct, int masteryLevel, int wrongStreak, Instant answeredAt, Instant nextReviewAt) {
        if (masteryLevel < 0 || masteryLevel > 4 || wrongStreak < 0) throw new IllegalArgumentException("Invalid progress level or streak.");
        if (correct && wrongStreak != 0) throw new IllegalArgumentException("A correct answer must reset the wrong streak.");
        var time = Objects.requireNonNull(answeredAt);
        var review = Objects.requireNonNull(nextReviewAt);
        if (review.isBefore(time) || (lastAnsweredAt != null && time.isBefore(lastAnsweredAt))) {
            throw new IllegalArgumentException("Invalid review timestamps.");
        }
        var nextAttempts = Math.incrementExact(attempts);
        var nextCorrect = correct ? Math.incrementExact(correctCount) : correctCount;
        var nextWrong = correct ? wrongCount : Math.incrementExact(wrongCount);
        this.attempts = nextAttempts;
        this.correctCount = nextCorrect;
        this.wrongCount = nextWrong;
        this.masteryLevel = masteryLevel;
        this.wrongStreak = wrongStreak;
        this.lastAnsweredAt = time;
        this.nextReviewAt = review;
    }
}
