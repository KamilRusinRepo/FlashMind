package com.example.backend.deck.review;

import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckType;
import com.example.backend.user.User;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeckReviewTests {
    private DeckReview review(int rating, String comment) {
        var user = new User("Reviewer", "reviewer@example.invalid", "test-hash");
        var owner = new User("Owner", "owner@example.invalid", "test-hash");
        return new DeckReview(user, new Deck(owner, "Deck", DeckType.GENERAL, "English", null), rating, comment);
    }

    @Test void supportsRatingBoundariesAndOptionalNormalizedComments() {
        var review = review(1, null);
        assertEquals(1, review.getRating()); assertNull(review.getComment());
        review.update(5, "  Useful deck!  ");
        assertEquals(5, review.getRating()); assertEquals("Useful deck!", review.getComment());
        review.update(4, " \n\t "); assertNull(review.getComment());
        review.update(3, "a".repeat(1000)); assertEquals(1000, review.getComment().length());
    }

    @Test void rejectsInvalidRatingsAndLongCommentsWithoutPartiallyChangingTheReview() {
        for (int rating : new int[]{-1, 0, 6}) assertThrows(IllegalArgumentException.class, () -> review(rating, null));
        var review = review(4, "Original");
        assertThrows(IllegalArgumentException.class, () -> review.update(5, "a".repeat(1001)));
        assertEquals(4, review.getRating()); assertEquals("Original", review.getComment());
    }
}
