package com.example.backend.admin.dto.response;

import java.util.List;
import com.example.backend.admin.dto.response.AdminUserPageResponse.Summary;
import com.example.backend.explore.dto.response.PublicDeckResponse;

public record AdminDeckPageResponse(List<PublicDeckResponse> content, int page, int size,
                                    long totalElements, int totalPages, Summary summary) {
    public AdminDeckPageResponse { content = List.copyOf(content); }
}
