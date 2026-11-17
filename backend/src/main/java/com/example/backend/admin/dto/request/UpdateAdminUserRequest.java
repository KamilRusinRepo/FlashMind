package com.example.backend.admin.dto.request;

import java.util.Locale;
import java.util.Set;
import com.example.backend.role.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAdminUserRequest(
        @NotBlank(message = "Username is required.")
        @Size(min = 3, max = 50, message = "Username must contain between 3 and 50 characters.")
        @Pattern(regexp = "[a-zA-Z0-9_.-]+", message = "Username may contain letters, digits, dots, hyphens and underscores.") String username,
        @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email must not exceed 255 characters.") String email,
        @NotEmpty(message = "Choose at least one role for this account.") @Size(max = 2) Set<@NotNull RoleName> roles
) {
    public UpdateAdminUserRequest {
        username = username == null ? null : username.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
    @Override public String toString() { return "UpdateAdminUserRequest[content=REDACTED]"; }
}
