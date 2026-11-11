package com.example.backend.admin;

import java.util.Locale;

import com.example.backend.auth.validation.PasswordByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        @NotBlank(message = "ADMIN_USERNAME is required.")
        @Size(min = 3, max = 50, message = "ADMIN_USERNAME must contain between 3 and 50 characters.")
        @Pattern(regexp = "[a-zA-Z0-9_.-]+", message = "ADMIN_USERNAME contains unsupported characters.")
        String username,

        @NotBlank(message = "ADMIN_EMAIL is required.")
        @Email(message = "ADMIN_EMAIL must be a valid email address.")
        @Size(max = 255, message = "ADMIN_EMAIL must not exceed 255 characters.")
        String email,

        @NotBlank(message = "ADMIN_PASSWORD is required.")
        @Size(min = 8, max = 72, message = "ADMIN_PASSWORD must contain between 8 and 72 characters.")
        @PasswordByteLength(message = "ADMIN_PASSWORD must not exceed 72 UTF-8 bytes.")
        String password
) {
    public AdminProperties {
        username = username == null ? "" : username.trim();
        email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        password = password == null ? "" : password;
    }

    public boolean isEmpty() {
        return username.isBlank() && email.isBlank() && password.isBlank();
    }

    @Override
    public String toString() {
        return "AdminProperties[credentials=REDACTED]";
    }
}
