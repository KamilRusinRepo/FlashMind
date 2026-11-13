package com.example.backend.auth.dto.request;

import com.example.backend.auth.validation.PasswordByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "A reset token is required.")
        @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "The reset token is invalid.")
        String token,
        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters.")
        @PasswordByteLength
        String password
) {
    @Override
    public String toString() { return "ResetPasswordRequest[token=REDACTED, password=REDACTED]"; }
}
