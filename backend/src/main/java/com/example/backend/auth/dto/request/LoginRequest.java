package com.example.backend.auth.dto.request;

import com.example.backend.auth.validation.PasswordByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email or username is required.")
        @Size(max = 255, message = "Email or username must not exceed 255 characters.") String login,
        @NotBlank(message = "Password is required.")
        @Size(max = 72, message = "Password must not exceed 72 characters.")
        @PasswordByteLength String password
) {
    public LoginRequest {
        login = login == null ? null : login.trim();
    }

    @Override
    public String toString() { return "LoginRequest[credentials=REDACTED]"; }
}
