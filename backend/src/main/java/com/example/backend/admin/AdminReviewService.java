package com.example.backend.admin;

import com.example.backend.admin.dto.response.AdminReviewPageResponse;
import com.example.backend.admin.dto.response.AdminReviewResponse;
import com.example.backend.auth.SessionUser;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.review.DeckReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminReviewService {
    private final DeckReviewRepository reviews;
    private final DeckRepository decks;
    private final AdminActorService actor;

    public AdminReviewService(DeckReviewRepository reviews, DeckRepository decks, AdminActorService actor) {
        this.reviews = reviews; this.decks = decks; this.actor = actor;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminReviewPageResponse list(int page, int size) {
        // Include preserved reviews after a deck becomes private, for moderation only.
        var result = reviews.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new AdminReviewPageResponse(result.map(AdminReviewResponse::from).getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public void delete(SessionUser principal, Long reviewId) {
        actor.requireActiveForUpdate(principal);
        // Read only the FK before locking; load the entity after the deck lock to avoid stale state.
        var deckId = reviews.findDeckId(reviewId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        decks.findForUpdate(deckId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var review = reviews.findById(reviewId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        reviews.delete(review);
        reviews.flush();
    }
}
