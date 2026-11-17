package com.example.backend.explore.dto.response;

import java.util.List;

public record PublicDeckPageResponse(List<PublicDeckResponse> content, int page, int size,
                                     long totalElements, int totalPages) {}
