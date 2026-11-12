package com.example.backend.auth.dto.response;

import java.time.Instant;
import java.util.Map;

import com.example.backend.error.ApiError;

/** Keeps the common error fields and adds resend details only after password verification. */
public record EmailVerificationRequiredResponse(
        Instant timestamp, int status, String error, String message, String path,
        Map<String, String> fieldErrors, String code, String email
) {
    public static EmailVerificationRequiredResponse from(ApiError error, String email) {
        return new EmailVerificationRequiredResponse(error.timestamp(), error.status(), error.error(),
                error.message(), error.path(), error.fieldErrors(), "EMAIL_VERIFICATION_REQUIRED", email);
    }
}
