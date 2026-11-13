package com.example.backend.mail;

import java.time.Instant;

/** Internal payload: never log it or return it through the API. */
public record PasswordResetEmailRequested(Long userId, String recipient, String token, Instant expiresAt) {
    @Override
    public String toString() { return "PasswordResetEmailRequested[userId=" + userId + ", payload=REDACTED]"; }
}
