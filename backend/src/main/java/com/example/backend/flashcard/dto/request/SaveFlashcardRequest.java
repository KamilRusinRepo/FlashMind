package com.example.backend.flashcard.dto.request;

import com.example.backend.flashcard.validation.ImageUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveFlashcardRequest(
        @NotBlank(message = "Question is required.") @Size(max = 2000, message = "Use at most 2000 characters.") String question,
        @NotBlank(message = "Answer is required.") @Size(max = 2000, message = "Use at most 2000 characters.") String answer,
        @Size(max = 1000, message = "Use at most 1000 characters.") String exampleSentence,
        @Size(max = 1000, message = "Use at most 1000 characters.") String translatedExample,
        @ImageUrl @Size(max = 2048, message = "Use at most 2048 characters.") String imageUrl
) {
    public SaveFlashcardRequest {
        question = normalize(question);
        answer = normalize(answer);
        exampleSentence = normalize(exampleSentence);
        translatedExample = normalize(translatedExample);
        imageUrl = normalize(imageUrl);
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    @Override public String toString() { return "SaveFlashcardRequest[content=REDACTED]"; }
}
