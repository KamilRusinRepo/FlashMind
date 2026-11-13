package com.example.backend.auth.dto.response;

public record ForgotPasswordResponse(String message, int retryAfterSeconds) {}
