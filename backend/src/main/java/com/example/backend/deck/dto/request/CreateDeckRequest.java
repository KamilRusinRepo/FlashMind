package com.example.backend.deck.dto.request;

import com.example.backend.deck.DeckType;
import com.example.backend.deck.validation.ValidDeckDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidDeckDetails
public record CreateDeckRequest(
        @NotBlank(message = "Deck name is required.") @Size(max = 120, message = "Use at most 120 characters.") String name,
        @Size(max = 2000, message = "Use at most 2000 characters.") String description,
        @NotNull(message = "Select a deck type.") DeckType deckType,
        @NotBlank(message = "Source language is required.") @Size(max = 60, message = "Use at most 60 characters.") String sourceLanguage,
        @Size(max = 60, message = "Use at most 60 characters.") String targetLanguage,
        @Size(max = 60, message = "Use at most 60 characters.") String category,
        @Size(max = 2048, message = "Use at most 2048 characters.") String backgroundUrl,
        Boolean publicDeck
) {
    public CreateDeckRequest {
        name = normalize(name);
        description = normalize(description);
        sourceLanguage = normalize(sourceLanguage);
        targetLanguage = deckType == DeckType.GENERAL ? null : normalize(targetLanguage);
        category = normalize(category);
        backgroundUrl = normalize(backgroundUrl);
        publicDeck = Boolean.TRUE.equals(publicDeck);
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    @Override public String toString() { return "CreateDeckRequest[content=REDACTED]"; }
}
