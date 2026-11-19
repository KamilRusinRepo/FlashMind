package com.example.backend.flashcard.csv;

import org.springframework.http.HttpStatus;

public class CsvPreviewException extends RuntimeException {
    private final HttpStatus status;
    public CsvPreviewException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
}
