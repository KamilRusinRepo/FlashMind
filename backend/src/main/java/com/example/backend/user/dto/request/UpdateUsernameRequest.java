package com.example.backend.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUsernameRequest(
        @NotBlank(message = "Username is required.")
        @Size(min = 3, max = 50, message = "Username must contain between 3 and 50 characters.")
        @Pattern(regexp = "[a-zA-Z0-9_.-]+", message = "Username may contain letters, digits, dots, hyphens and underscores.")
        String username) {
    public UpdateUsernameRequest { username = username == null ? null : username.trim(); }
}
