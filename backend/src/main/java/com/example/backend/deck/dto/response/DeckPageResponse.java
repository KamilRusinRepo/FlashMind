package com.example.backend.deck.dto.response;

import java.util.List;

public record DeckPageResponse(List<DeckListItemResponse> content, int page, int size, long totalElements, int totalPages) {
    public DeckPageResponse { content = List.copyOf(content); }
}
