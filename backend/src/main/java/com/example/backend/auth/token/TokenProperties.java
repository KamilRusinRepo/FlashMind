package com.example.backend.auth.token;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tokens")
public record TokenProperties(Duration emailVerificationTtl, Duration passwordResetTtl) {

    public TokenProperties {
        requirePositive(emailVerificationTtl);
        requirePositive(passwordResetTtl);
    }

    private static void requirePositive(Duration value) {
        Objects.requireNonNull(value, "Token lifetime must be configured.");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("Token lifetime must be positive.");
        }
    }
}
