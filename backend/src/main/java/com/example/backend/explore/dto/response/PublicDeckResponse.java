package com.example.backend.explore.dto.response;

import java.time.Instant;
import com.example.backend.deck.DeckType;
import com.example.backend.deck.dto.response.DeckAuthorResponse;

public record PublicDeckResponse(Long id, String name, String description, DeckType deckType,
        String category, String sourceLanguage, String targetLanguage, String backgroundUrl,
        Instant createdAt, long cardCount, DeckAuthorResponse author, boolean ownDeck, boolean following, long followerCount,
        Double averageRating, long reviewCount) {}
