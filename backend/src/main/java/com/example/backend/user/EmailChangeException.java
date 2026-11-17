package com.example.backend.user;

import org.springframework.http.HttpStatus;

public class EmailChangeException extends RuntimeException {
    private final HttpStatus status;
    private final String field;
    public EmailChangeException(HttpStatus status, String field, String message) {
        super(message); this.status = status; this.field = field;
    }
    public HttpStatus getStatus() { return status; }
    public String getField() { return field; }
}
