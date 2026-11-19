package com.example.backend.deck.review.dto.response;

import java.time.Instant;
import com.example.backend.deck.dto.response.DeckAuthorResponse;
import com.example.backend.deck.review.DeckReview;

public record DeckReviewResponse(Long id, DeckAuthorResponse author, int rating, String comment,
                                 Instant createdAt, Instant updatedAt, boolean ownReview) {
    public static DeckReviewResponse from(DeckReview review, Long viewerId) {
        return new DeckReviewResponse(review.getId(), DeckAuthorResponse.from(review.getUser()), review.getRating(),
                review.getComment(), review.getCreatedAt(), review.getUpdatedAt(), review.getUser().getId().equals(viewerId));
    }
}
