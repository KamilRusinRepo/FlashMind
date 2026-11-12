package com.example.backend.auth.dto.response;

import java.util.List;

public record LoginResponse(Long id, String username, String email, List<String> roles) {
}
