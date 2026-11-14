package com.example.backend.study.dto.response;

import java.time.Instant;
import java.util.List;
import com.example.backend.deck.dto.response.DeckListItemResponse;
import com.example.backend.study.StudySessionStatus;

public record StudySessionResponse(Long id, DeckListItemResponse deck, int totalCards, StudySessionStatus status,
                                    Instant startedAt, List<StudyQuestionResponse> cards) {
    public StudySessionResponse { cards = List.copyOf(cards); }
}
