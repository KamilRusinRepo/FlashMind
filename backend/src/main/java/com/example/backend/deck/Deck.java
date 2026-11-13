package com.example.backend.deck;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "decks")
public class Deck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No JPA cascade: saving/deleting a deck must never save/delete its owner.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", updatable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "deck_type", nullable = false, length = 20)
    private DeckType deckType;

    @Column(name = "source_language", nullable = false, length = 60)
    private String sourceLanguage;

    @Column(name = "target_language", length = 60)
    private String targetLanguage;

    @Column(length = 60)
    private String category;

    @Column(name = "background_url", columnDefinition = "text")
    private String backgroundUrl;

    @Column(name = "is_public", nullable = false)
    private boolean publicDeck;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Deck() {}

    public Deck(User owner, String name, DeckType deckType, String sourceLanguage, String targetLanguage) {
        this.owner = Objects.requireNonNull(owner);
        setName(name);
        setLearningLanguages(deckType, sourceLanguage, targetLanguage);
    }

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public DeckType getDeckType() { return deckType; }
    public String getSourceLanguage() { return sourceLanguage; }
    public String getTargetLanguage() { return targetLanguage; }
    public String getCategory() { return category; }
    public String getBackgroundUrl() { return backgroundUrl; }
    public boolean isPublic() { return publicDeck; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = requiredText(name); }
    public void setDescription(String description) { this.description = optionalText(description); }
    public void setCategory(String category) { this.category = optionalText(category); }
    public void setBackgroundUrl(String backgroundUrl) { this.backgroundUrl = optionalText(backgroundUrl); }
    public void setPublic(boolean publicDeck) { this.publicDeck = publicDeck; }

    public void setLearningLanguages(DeckType deckType, String sourceLanguage, String targetLanguage) {
        var type = Objects.requireNonNull(deckType);
        var source = requiredText(sourceLanguage);
        var target = type == DeckType.LANGUAGE ? requiredText(targetLanguage) : null;
        this.deckType = type;
        this.sourceLanguage = source;
        this.targetLanguage = target;
    }

    private static String requiredText(String value) {
        var text = Objects.requireNonNull(value).trim();
        if (text.isBlank()) throw new IllegalArgumentException("Required deck text must not be blank.");
        return text;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
