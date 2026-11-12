package com.example.backend.auth;

import org.springframework.security.core.AuthenticationException;

public class EmailNotVerifiedException extends AuthenticationException {
    private final String email;

    public EmailNotVerifiedException(String email) {
        super("Confirm your email address before signing in.");
        this.email = email;
    }

    public String getEmail() { return email; }
}
