package com.example.backend.flashcard.dto.response;

import java.util.List;

public record FlashcardPageResponse(List<FlashcardResponse> content, int page, int size, long totalElements, int totalPages) {
    public FlashcardPageResponse { content = List.copyOf(content); }
}
