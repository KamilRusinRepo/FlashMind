package com.example.backend.deck.review.dto.request;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDeckReviewRequest(
        // Preserve fractional JSON numbers until validation instead of silently truncating them.
        @Schema(type = "integer", minimum = "1", maximum = "5")
        @NotNull(message = "Choose a whole-star rating from 1 to 5.")
        @DecimalMin(value = "1", message = "Choose a whole-star rating from 1 to 5.")
        @DecimalMax(value = "5", message = "Choose a whole-star rating from 1 to 5.")
        @Digits(integer = 1, fraction = 0, message = "Choose a whole-star rating from 1 to 5.") BigDecimal rating,
        @Size(max = 1000, message = "Use at most 1000 characters.") String comment
) {
    public CreateDeckReviewRequest { comment = comment == null || comment.isBlank() ? null : comment.trim(); }
    @Override public String toString() { return "CreateDeckReviewRequest[content=REDACTED]"; }
}
