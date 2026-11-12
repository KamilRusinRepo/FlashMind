package com.example.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
        @NotBlank(message = "A confirmation token is required.")
        @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "The confirmation token is invalid.")
        String token
) {
    @Override
    public String toString() {
        return "VerifyEmailRequest[token=REDACTED]";
    }
}
