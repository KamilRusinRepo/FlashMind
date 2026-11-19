package com.example.backend.deck.review;

import com.example.backend.auth.SessionUser;
import com.example.backend.deck.review.dto.response.DeckReviewPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/explore/decks/{deckId}/reviews")
public class DeckReviewController {
    private final DeckReviewService reviews;
    public DeckReviewController(DeckReviewService reviews) { this.reviews = reviews; }

    @Operation(summary = "List public deck reviews and overall rating statistics",
            description = "Signed-in users only. Newest first, including reviews of authorless public decks. Summary covers every review, not only this page. No ratings gives averageRating=null.")
    @GetMapping
    public ResponseEntity<DeckReviewPageResponse> list(@AuthenticationPrincipal SessionUser viewer,
            @PathVariable("deckId") @Min(1) Long deckId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "5") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.listPublic(viewer.id(), deckId, page, size));
    }
}
