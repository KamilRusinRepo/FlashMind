package com.example.backend.flashcard;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

import com.example.backend.deck.Deck;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "flashcards")
public class Flashcard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No JPA cascade: a flashcard must never save or delete its deck.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false, updatable = false)
    private Deck deck;

    @Column(nullable = false, length = 2000)
    private String question;

    @Column(nullable = false, length = 2000)
    private String answer;

    @Column(name = "example_sentence", length = 1000)
    private String exampleSentence;

    @Column(name = "translated_example", length = 1000)
    private String translatedExample;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Flashcard() {}

    public Flashcard(Deck deck, String question, String answer) {
        this.deck = Objects.requireNonNull(deck);
        setQuestion(question);
        setAnswer(answer);
    }

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Deck getDeck() { return deck; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public String getExampleSentence() { return exampleSentence; }
    public String getTranslatedExample() { return translatedExample; }
    public String getImageUrl() { return imageUrl; }
    public Instant getCreatedAt() { return createdAt; }

    public void setQuestion(String question) { this.question = requiredText(question); }
    public void setAnswer(String answer) { this.answer = requiredText(answer); }
    public void setExampleSentence(String value) { this.exampleSentence = optionalText(value, 1000); }
    public void setTranslatedExample(String value) { this.translatedExample = optionalText(value, 1000); }

    public void setImageUrl(String value) {
        var url = optionalText(value, 2048);
        if (url != null) {
            var uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Image URL must be an absolute HTTP or HTTPS URL without embedded credentials.");
            }
        }
        this.imageUrl = url;
    }

    private static String requiredText(String value) {
        var text = Objects.requireNonNull(value).trim();
        if (text.isBlank() || text.length() > 2000) {
            throw new IllegalArgumentException("Flashcard question and answer must contain 1 to 2000 characters.");
        }
        return text;
    }

    private static String optionalText(String value, int maxLength) {
        var text = value == null || value.isBlank() ? null : value.trim();
        if (text != null && text.length() > maxLength) {
            throw new IllegalArgumentException("Optional flashcard text exceeds its maximum length.");
        }
        return text;
    }
}
