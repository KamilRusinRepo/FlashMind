package com.example.backend.mail;

import java.time.Instant;

public record EmailChangeRequested(Long userId, String recipient, String token, Instant expiresAt) {
    @Override public String toString() { return "EmailChangeRequested[userId=" + userId + ", payload=REDACTED]"; }
}
