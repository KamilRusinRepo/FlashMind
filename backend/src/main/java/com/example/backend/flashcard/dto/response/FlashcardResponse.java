package com.example.backend.flashcard.dto.response;

import java.time.Instant;
import com.example.backend.flashcard.Flashcard;

public record FlashcardResponse(Long id, String question, String answer, String exampleSentence,
                                String translatedExample, String imageUrl, Instant createdAt) {
    public static FlashcardResponse from(Flashcard card) {
        return new FlashcardResponse(card.getId(), card.getQuestion(), card.getAnswer(), card.getExampleSentence(),
                card.getTranslatedExample(), card.getImageUrl(), card.getCreatedAt());
    }
}
