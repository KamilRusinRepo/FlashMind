package com.example.backend.user.dto.response;

import java.time.Instant;

public record PendingEmailChangeResponse(String pendingEmail, Instant expiresAt, int retryAfterSeconds) {}
