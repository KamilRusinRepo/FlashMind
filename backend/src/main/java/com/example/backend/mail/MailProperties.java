package com.example.backend.mail;

import java.net.URI;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.mail")
public record MailProperties(@NotBlank @Email String from, @NotNull URI verificationUrl) {
    public MailProperties {
        if (verificationUrl != null && (!verificationUrl.isAbsolute()
                || !("http".equals(verificationUrl.getScheme()) || "https".equals(verificationUrl.getScheme()))
                || verificationUrl.getHost() == null || verificationUrl.getRawQuery() != null
                || verificationUrl.getRawFragment() != null || verificationUrl.getUserInfo() != null)) {
            throw new IllegalArgumentException("Mail verification URL must be an absolute HTTP(S) URL without query or fragment.");
        }
    }
}
