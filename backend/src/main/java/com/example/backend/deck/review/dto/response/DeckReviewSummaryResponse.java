package com.example.backend.deck.review.dto.response;

import java.util.List;

public record DeckReviewSummaryResponse(Double averageRating, long reviewCount, List<RatingBucket> distribution) {
    public record RatingBucket(int rating, long count) { }
}
