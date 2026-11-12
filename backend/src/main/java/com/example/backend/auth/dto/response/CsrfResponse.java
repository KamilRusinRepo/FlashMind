package com.example.backend.auth.dto.response;

public record CsrfResponse(String headerName, String token) {
    @Override
    public String toString() {
        return "CsrfResponse[headerName=" + headerName + ", token=REDACTED]";
    }
}
