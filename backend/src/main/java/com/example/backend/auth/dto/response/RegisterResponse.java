package com.example.backend.auth.dto.response;

import java.time.Instant;

public record RegisterResponse(
        Long id,
        String username,
        String email,
        boolean emailVerified,
        Instant createdAt
) {
}
