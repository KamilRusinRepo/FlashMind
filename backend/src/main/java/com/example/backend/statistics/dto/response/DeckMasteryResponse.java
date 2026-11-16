package com.example.backend.statistics.dto.response;

import com.example.backend.deck.DeckType;

public record DeckMasteryResponse(Long id, String name, long total, long mastered,
        int masteryPercent, long correct, long wrong, DeckType deckType, long dueCards) {}
