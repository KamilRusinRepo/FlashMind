package com.example.backend.auth.dto.response;

import java.util.List;

public record CurrentUserResponse(Long id, String username, String email, List<String> roles) {
}
