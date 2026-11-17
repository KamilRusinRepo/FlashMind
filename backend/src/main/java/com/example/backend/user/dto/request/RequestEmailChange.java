package com.example.backend.user.dto.request;

import java.util.Locale;
import com.example.backend.auth.validation.PasswordByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestEmailChange(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 72) @PasswordByteLength String currentPassword) {
    public RequestEmailChange { email = email == null ? null : email.trim().toLowerCase(Locale.ROOT); }
    @Override public String toString() { return "RequestEmailChange[payload=REDACTED]"; }
}
