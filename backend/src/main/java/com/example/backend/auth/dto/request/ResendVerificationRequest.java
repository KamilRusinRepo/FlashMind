package com.example.backend.auth.dto.request;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email must not exceed 255 characters.")
        String email
) {
    public ResendVerificationRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "ResendVerificationRequest[email=REDACTED]";
    }
}
