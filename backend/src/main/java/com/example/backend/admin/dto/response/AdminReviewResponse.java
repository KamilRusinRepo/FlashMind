package com.example.backend.admin.dto.response;

import java.time.Instant;
import com.example.backend.deck.dto.response.DeckAuthorResponse;
import com.example.backend.deck.review.DeckReview;

public record AdminReviewResponse(Long id, DeckInfo deck, DeckAuthorResponse author, int rating, String comment,
                                  Instant createdAt, Instant updatedAt) {
    public record DeckInfo(Long id, String name, boolean publicDeck) { }

    public static AdminReviewResponse from(DeckReview review) {
        var deck = review.getDeck();
        return new AdminReviewResponse(review.getId(), new DeckInfo(deck.getId(), deck.getName(), deck.isPublic()),
                DeckAuthorResponse.from(review.getUser()), review.getRating(), review.getComment(), review.getCreatedAt(), review.getUpdatedAt());
    }
}
