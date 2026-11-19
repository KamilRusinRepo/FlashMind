package com.example.backend.deck.review;

import java.util.ArrayList;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.review.dto.response.DeckReviewPageResponse;
import com.example.backend.deck.review.dto.response.DeckReviewResponse;
import com.example.backend.deck.review.dto.response.DeckReviewSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeckReviewService {
    private final DeckRepository decks;
    private final DeckReviewRepository reviews;
    public DeckReviewService(DeckRepository decks, DeckReviewRepository reviews) { this.decks = decks; this.reviews = reviews; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DeckReviewPageResponse listPublic(Long viewerId, Long deckId, int page, int size) {
        decks.findById(deckId).filter(deck -> deck.isPublic()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        long[] counts = new long[6];
        for (var bucket : reviews.countRatings(deckId)) counts[bucket.getRating()] = bucket.getTotal();
        long total = 0;
        double weighted = 0;
        var distribution = new ArrayList<DeckReviewSummaryResponse.RatingBucket>();
        for (int rating = 5; rating >= 1; rating--) {
            total += counts[rating]; weighted += counts[rating] * (double) rating;
            distribution.add(new DeckReviewSummaryResponse.RatingBucket(rating, counts[rating]));
        }
        var result = reviews.findByDeck_Id(deckId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new DeckReviewPageResponse(result.map(review -> DeckReviewResponse.from(review, viewerId)).getContent(), page, size,
                total, (int) ((total + size - 1) / size), new DeckReviewSummaryResponse(total == 0 ? null : weighted / total, total, distribution));
    }
}
