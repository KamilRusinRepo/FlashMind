package com.example.backend.deck.review;

import java.util.ArrayList;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.review.dto.response.DeckReviewPageResponse;
import com.example.backend.deck.review.dto.response.DeckReviewResponse;
import com.example.backend.deck.review.dto.response.DeckReviewSummaryResponse;
import com.example.backend.deck.review.dto.request.CreateDeckReviewRequest;
import com.example.backend.auth.SessionUser;
import com.example.backend.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
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
    private final UserRepository users;
    public DeckReviewService(DeckRepository decks, DeckReviewRepository reviews, UserRepository users) { this.decks = decks; this.reviews = reviews; this.users = users; }

    @Transactional
    public DeckReviewResponse createPublic(SessionUser principal, Long deckId, CreateDeckReviewRequest request) {
        var user = users.findByIdForUpdate(principal.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getSessionVersion() != principal.sessionVersion()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!user.isEnabled() || user.getEmailVerifiedAt() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var deck = decks.findForUpdate(deckId).filter(value -> value.isPublic()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (deck.getOwner() != null && deck.getOwner().getId().equals(principal.id()))
            throw new DeckReviewConflictException("You cannot review your own deck.");
        if (reviews.existsByUser_IdAndDeck_Id(principal.id(), deckId))
            throw new DeckReviewConflictException("You have already reviewed this deck.");
        try {
            return DeckReviewResponse.from(reviews.saveAndFlush(new DeckReview(user, deck, request.rating().intValueExact(), request.comment())), principal.id());
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation && "uq_deck_reviews_user_deck".equals(violation.getConstraintName()))
                    throw new DeckReviewConflictException("You have already reviewed this deck.");
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DeckReviewPageResponse listPublic(Long viewerId, Long deckId, int page, int size) {
        decks.findById(deckId).filter(deck -> deck.isPublic()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return readPage(viewerId, deckId, page, size);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DeckReviewPageResponse listOwned(Long ownerId, Long deckId, int page, int size) {
        decks.findByIdAndOwner_Id(deckId, ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return readPage(ownerId, deckId, page, size);
    }

    private DeckReviewPageResponse readPage(Long viewerId, Long deckId, int page, int size) {
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
                total, (int) ((total + size - 1) / size), new DeckReviewSummaryResponse(total == 0 ? null : weighted / total, total, distribution),
                reviews.findByUser_IdAndDeck_Id(viewerId, deckId).map(review -> DeckReviewResponse.from(review, viewerId)).orElse(null));
    }
}
