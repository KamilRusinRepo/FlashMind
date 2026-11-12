package com.example.backend.auth.token;

public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("The token is invalid, expired or has already been used.");
    }
}
