package com.example.backend.deck.dto.response;

import java.time.Instant;

import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckType;

public record DeckListItemResponse(Long id, String name, String description, DeckType deckType,
                                   String sourceLanguage, String targetLanguage, String category,
                                   String backgroundUrl, boolean publicDeck, Instant createdAt) {
    public static DeckListItemResponse from(Deck deck) {
        return new DeckListItemResponse(deck.getId(), deck.getName(), deck.getDescription(), deck.getDeckType(),
                deck.getSourceLanguage(), deck.getTargetLanguage(), deck.getCategory(), deck.getBackgroundUrl(), deck.isPublic(), deck.getCreatedAt());
    }
}
