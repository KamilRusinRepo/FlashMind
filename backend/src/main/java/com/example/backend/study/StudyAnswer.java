package com.example.backend.study;

import java.time.Instant;
import java.util.Objects;
import com.example.backend.flashcard.Flashcard;
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

@Entity
@Table(name = "study_answers", uniqueConstraints = {
        @UniqueConstraint(name = "uq_study_answers_position", columnNames = {"session_id", "position"}),
        @UniqueConstraint(name = "uq_study_answers_flashcard", columnNames = {"session_id", "flashcard_id"})
})
public class StudyAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private StudySession session;

    // Nullable after deletion; snapshots preserve what was practised in this session.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flashcard_id", updatable = false)
    private Flashcard flashcard;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(name = "question_snapshot", nullable = false, length = 2000, updatable = false)
    private String questionSnapshot;
    @Column(name = "answer_snapshot", nullable = false, length = 2000, updatable = false)
    private String answerSnapshot;
    @Column(name = "example_sentence_snapshot", length = 1000, updatable = false)
    private String exampleSentenceSnapshot;
    @Column(name = "translated_example_snapshot", length = 1000, updatable = false)
    private String translatedExampleSnapshot;
    @Column(name = "image_url_snapshot", length = 2048, updatable = false)
    private String imageUrlSnapshot;

    @Column(name = "user_answer", length = 2000)
    private String userAnswer;
    @Column(name = "was_correct")
    private Boolean correct;
    @Column(name = "used_hint", nullable = false)
    private boolean usedHint;
    @Column(nullable = false)
    private boolean skipped;
    @Column(name = "answered_at")
    private Instant answeredAt;

    protected StudyAnswer() {}

    public StudyAnswer(StudySession session, Flashcard flashcard, int position) {
        this.session = Objects.requireNonNull(session);
        this.flashcard = Objects.requireNonNull(flashcard);
        if (!session.isActive()) throw new IllegalStateException("This study session has already ended.");
        if (position < 1 || position > session.getTotalCards()) throw new IllegalArgumentException("Invalid flashcard position.");
        var deckId = session.getDeck().getId();
        if (session.getDeck() != flashcard.getDeck() && (deckId == null || !deckId.equals(flashcard.getDeck().getId()))) {
            throw new IllegalArgumentException("The flashcard must belong to the session deck.");
        }
        this.position = position;
        this.questionSnapshot = flashcard.getQuestion();
        this.answerSnapshot = flashcard.getAnswer();
        this.exampleSentenceSnapshot = flashcard.getExampleSentence();
        this.translatedExampleSnapshot = flashcard.getTranslatedExample();
        this.imageUrlSnapshot = flashcard.getImageUrl();
    }

    public Long getId() { return id; }
    public StudySession getSession() { return session; }
    public Flashcard getFlashcard() { return flashcard; }
    public int getPosition() { return position; }
    public String getQuestionSnapshot() { return questionSnapshot; }
    public String getAnswerSnapshot() { return answerSnapshot; }
    public String getExampleSentenceSnapshot() { return exampleSentenceSnapshot; }
    public String getTranslatedExampleSnapshot() { return translatedExampleSnapshot; }
    public String getImageUrlSnapshot() { return imageUrlSnapshot; }
    public String getUserAnswer() { return userAnswer; }
    public Boolean getCorrect() { return correct; }
    public boolean isUsedHint() { return usedHint; }
    public boolean isSkipped() { return skipped; }
    public Instant getAnsweredAt() { return answeredAt; }
    public boolean isAnswered() { return answeredAt != null; }

    public void markHintUsed() {
        requirePending();
        this.usedHint = true;
    }

    // Correctness must be calculated by the future service, never accepted from an API client.
    public void recordAnswer(String userAnswer, boolean correct, boolean skipped, Instant answeredAt) {
        requirePending();
        var text = skipped ? "" : Objects.requireNonNull(userAnswer);
        if (text.length() > 2000) throw new IllegalArgumentException("Use at most 2000 answer characters.");
        if (skipped && correct) throw new IllegalArgumentException("A skipped answer cannot be correct.");
        var time = Objects.requireNonNull(answeredAt);
        if (time.isBefore(session.getStartedAt())) throw new IllegalArgumentException("An answer cannot precede its session.");
        this.userAnswer = text; // Preserve the submitted text, including spaces and letter case.
        this.correct = correct;
        this.skipped = skipped;
        this.answeredAt = time;
    }

    private void requirePending() {
        if (!session.isActive()) throw new IllegalStateException("This study session has already ended.");
        if (isAnswered()) throw new IllegalStateException("This flashcard has already been answered in this session.");
    }
}
