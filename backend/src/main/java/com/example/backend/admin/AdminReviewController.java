package com.example.backend.admin;

import com.example.backend.admin.dto.response.AdminReviewPageResponse;
import com.example.backend.auth.SessionUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {
    private final AdminReviewService reviews;
    public AdminReviewController(AdminReviewService reviews) { this.reviews = reviews; }

    @Operation(summary = "List reviews for moderation", description = "ADMIN only. Newest first; includes preserved reviews of private decks. Safe author and deck summaries, no account secrets.")
    @GetMapping
    public ResponseEntity<AdminReviewPageResponse> list(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "5") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.list(page, size));
    }

    @Operation(summary = "Delete a review as moderator", description = "Active verified ADMIN and CSRF required. Deletes only the review; rating aggregates reflect the deletion. Missing review returns 404.")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SessionUser principal, @PathVariable("reviewId") @Min(1) Long reviewId) {
        reviews.delete(principal, reviewId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
