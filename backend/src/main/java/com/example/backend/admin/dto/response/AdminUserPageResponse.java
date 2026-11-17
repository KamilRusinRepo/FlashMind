package com.example.backend.admin.dto.response;

import java.util.List;

public record AdminUserPageResponse(List<AdminUserResponse> content, int page, int size, long totalElements,
                                    int totalPages, Summary summary) {
    public AdminUserPageResponse { content = List.copyOf(content); }
    public record Summary(long users, long activeUsers, long blockedUsers, long publicDecks) {}
}
