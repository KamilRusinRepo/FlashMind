package com.example.backend.admin;

public class AdminUserEditException extends RuntimeException {
    private final String field;
    public AdminUserEditException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
