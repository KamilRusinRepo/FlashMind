package com.example.backend.user.dto.request;

import com.example.backend.auth.validation.PasswordByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required.")
        @Size(max = 72, message = "Password must not exceed 72 characters.")
        @PasswordByteLength String currentPassword,
        @NotBlank(message = "New password is required.")
        @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters.")
        @PasswordByteLength String newPassword,
        @NotBlank(message = "Confirm your new password.")
        @Size(max = 72, message = "Password must not exceed 72 characters.")
        @PasswordByteLength String confirmPassword) {
    @Override public String toString() { return "ChangePasswordRequest[passwords=REDACTED]"; }
}
