package com.example.backend.flashcard.csv;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class CsvPreviewException extends RuntimeException {
    private final HttpStatus status;
    private final Map<String, String> fieldErrors;
    public CsvPreviewException(HttpStatus status, String message) { this(status, message, Map.of("file", message)); }
    public CsvPreviewException(HttpStatus status, String message, Map<String, String> fieldErrors) {
        super(message); this.status = status; this.fieldErrors = Map.copyOf(fieldErrors);
    }
    public HttpStatus getStatus() { return status; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
