package com.example.backend.deck.review;

import com.example.backend.auth.SessionUser;
import com.example.backend.deck.review.dto.response.DeckReviewPageResponse;
import com.example.backend.deck.review.dto.response.DeckReviewResponse;
import com.example.backend.deck.review.dto.request.CreateDeckReviewRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeckReviewController {
    private final DeckReviewService reviews;
    public DeckReviewController(DeckReviewService reviews) { this.reviews = reviews; }

    @Operation(summary = "Review another user's public deck",
            description = "Requires an active verified session and CSRF. Whole-star rating 1–5, optional comment up to 1000 characters. One review per user/deck; own decks cannot be reviewed.")
    @PostMapping("/api/explore/decks/{deckId}/reviews")
    public ResponseEntity<DeckReviewResponse> create(@AuthenticationPrincipal SessionUser viewer,
            @PathVariable("deckId") @Min(1) Long deckId, @Valid @RequestBody CreateDeckReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(reviews.createPublic(viewer, deckId, request));
    }

    @Operation(summary = "Edit your own review of a public deck",
            description = "Author only, active verified session and CSRF required. Same rating/comment validation as creation. Private, missing and another user's reviews return 404.")
    @PutMapping("/api/explore/decks/{deckId}/reviews/{reviewId}")
    public ResponseEntity<DeckReviewResponse> update(@AuthenticationPrincipal SessionUser viewer,
            @PathVariable("deckId") @Min(1) Long deckId, @PathVariable("reviewId") @Min(1) Long reviewId,
            @Valid @RequestBody CreateDeckReviewRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.updateOwn(viewer, deckId, reviewId, request));
    }

    @Operation(summary = "Delete your own review of a public deck",
            description = "Author only, active verified session and CSRF required. Private, missing and another user's reviews return 404. Rating aggregates reflect the deletion immediately.")
    @DeleteMapping("/api/explore/decks/{deckId}/reviews/{reviewId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SessionUser viewer,
            @PathVariable("deckId") @Min(1) Long deckId, @PathVariable("reviewId") @Min(1) Long reviewId) {
        reviews.deleteOwn(viewer, deckId, reviewId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @Operation(summary = "List public deck reviews and overall rating statistics",
            description = "Signed-in users only. Newest first, including reviews of authorless public decks. Summary covers every review, not only this page. No ratings gives averageRating=null.")
    @GetMapping("/api/explore/decks/{deckId}/reviews")
    public ResponseEntity<DeckReviewPageResponse> list(@AuthenticationPrincipal SessionUser viewer,
            @PathVariable("deckId") @Min(1) Long deckId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "5") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.listPublic(viewer.id(), deckId, page, size));
    }

    @Operation(summary = "Read reviews of an owned deck, including a private deck",
            description = "Owner only. Reviews survive a change of deck visibility; private reviews are not exposed through Explore. Summary covers all reviews.")
    @GetMapping("/api/decks/{deckId}/reviews")
    public ResponseEntity<DeckReviewPageResponse> owned(@AuthenticationPrincipal SessionUser viewer,
            @PathVariable("deckId") @Min(1) Long deckId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "5") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.listOwned(viewer.id(), deckId, page, size));
    }
}
