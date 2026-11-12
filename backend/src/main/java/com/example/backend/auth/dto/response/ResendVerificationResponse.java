package com.example.backend.auth.dto.response;

public record ResendVerificationResponse(String message, int retryAfterSeconds) {
}
