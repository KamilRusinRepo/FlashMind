package com.example.backend.mail;

import java.net.URI;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.mail")
public record PasswordResetMailProperties(@NotNull URI passwordResetUrl) {
    public PasswordResetMailProperties {
        if (passwordResetUrl != null && (!passwordResetUrl.isAbsolute()
                || !("http".equals(passwordResetUrl.getScheme()) || "https".equals(passwordResetUrl.getScheme()))
                || passwordResetUrl.getHost() == null || passwordResetUrl.getRawQuery() != null
                || passwordResetUrl.getRawFragment() != null || passwordResetUrl.getUserInfo() != null)) {
            throw new IllegalArgumentException("Password reset URL must be an absolute HTTP(S) URL without query or fragment.");
        }
    }
}
