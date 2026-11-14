package com.example.backend.user;

public class UsernameConflictException extends RuntimeException {
    public UsernameConflictException() { super("This username is already taken."); }
}
