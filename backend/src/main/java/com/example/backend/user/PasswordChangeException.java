package com.example.backend.user;

public class PasswordChangeException extends RuntimeException {
    private final String field;
    public PasswordChangeException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
