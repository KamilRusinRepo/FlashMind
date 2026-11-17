package com.example.backend.deck.dto.response;

import java.time.Instant;

import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckType;

public record DeckListItemResponse(Long id, String name, String description, DeckType deckType,
                                   String sourceLanguage, String targetLanguage, String category,
                                   String backgroundUrl, boolean publicDeck, Instant createdAt, boolean ownDeck, DeckAuthorResponse author) {
    public static DeckListItemResponse from(Deck deck) {
        return from(deck, deck.getOwner() == null ? null : deck.getOwner().getId());
    }
    public static DeckListItemResponse from(Deck deck, Long viewerId) {
        return new DeckListItemResponse(deck.getId(), deck.getName(), deck.getDescription(), deck.getDeckType(),
                deck.getSourceLanguage(), deck.getTargetLanguage(), deck.getCategory(), deck.getBackgroundUrl(), deck.isPublic(), deck.getCreatedAt(),
                deck.getOwner() != null && deck.getOwner().getId().equals(viewerId), DeckAuthorResponse.from(deck.getOwner()));
    }
}
