package com.example.backend.admin.dto.response;

import java.util.List;

public record AdminReviewPageResponse(List<AdminReviewResponse> content, int page, int size, long totalElements, int totalPages) { }
