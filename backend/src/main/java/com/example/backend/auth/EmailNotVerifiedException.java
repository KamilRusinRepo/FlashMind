package com.example.backend.auth;

import org.springframework.security.core.AuthenticationException;

public class EmailNotVerifiedException extends AuthenticationException {
    public EmailNotVerifiedException() { super("Confirm your email address before signing in."); }
}
