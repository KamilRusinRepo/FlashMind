package com.example.backend.statistics.dto.response;

public record DeckMasteryResponse(Long id, String name, long total, long mastered,
        int masteryPercent, long correct, long wrong) {}
