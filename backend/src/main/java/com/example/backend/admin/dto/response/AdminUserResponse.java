package com.example.backend.admin.dto.response;

import java.time.Instant;
import java.util.List;
import com.example.backend.user.User;

public record AdminUserResponse(Long id, String username, String email, List<String> roles,
                                boolean enabled, boolean emailVerified, Instant createdAt) {
    public AdminUserResponse { roles = List.copyOf(roles); }
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRoles().stream().map(role -> role.getName().name()).sorted().toList(),
                user.isEnabled(), user.getEmailVerifiedAt() != null, user.getCreatedAt());
    }
}
