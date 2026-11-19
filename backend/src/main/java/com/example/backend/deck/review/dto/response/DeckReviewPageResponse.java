package com.example.backend.deck.review.dto.response;

import java.util.List;

public record DeckReviewPageResponse(List<DeckReviewResponse> content, int page, int size, long totalElements,
                                     int totalPages, DeckReviewSummaryResponse summary) { }
