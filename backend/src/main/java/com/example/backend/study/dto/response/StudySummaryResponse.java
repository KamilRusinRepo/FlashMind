package com.example.backend.study.dto.response;

import java.time.Instant;
import com.example.backend.study.StudyMode;

public record StudySummaryResponse(Long sessionId, Long deckId, String deckName, int totalCards,
                                    int correctCards, int wrongCards, int accuracy, int xpEarned,
                                    long durationSeconds, Instant startedAt, Instant endedAt, StudyMode mode) {
}
