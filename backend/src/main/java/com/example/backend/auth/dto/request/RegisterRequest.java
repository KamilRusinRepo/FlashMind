package com.example.backend.auth.dto.request;

import java.util.Locale;

import com.example.backend.auth.validation.PasswordByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Username is required.")
        @Size(min = 3, max = 50, message = "Username must contain between 3 and 50 characters.")
        @Pattern(regexp = "[a-zA-Z0-9_.-]+", message = "Username may contain letters, digits, dots, hyphens and underscores.")
        String username,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email must not exceed 255 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters.")
        @PasswordByteLength
        String password
) {
    public RegisterRequest {
        username = username == null ? null : username.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RegisterRequest[password=REDACTED]";
    }
}
