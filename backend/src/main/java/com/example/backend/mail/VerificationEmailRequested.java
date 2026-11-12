package com.example.backend.mail;

import java.time.Instant;

/** Internal message: never return it from the API or log its payload. */
public record VerificationEmailRequested(Long userId, String recipient, String token, Instant expiresAt) {
    @Override
    public String toString() {
        return "VerificationEmailRequested[userId=" + userId + ", payload=REDACTED]";
    }
}
