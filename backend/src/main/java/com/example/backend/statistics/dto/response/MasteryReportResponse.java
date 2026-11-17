package com.example.backend.statistics.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MasteryReportResponse(Long id, String name, long total, int masteryPercent,
        LocalDate today, String zone, List<Bucket> buckets, List<Card> content,
        int page, int size, long totalElements, int totalPages) {
    public record Bucket(int level, long count) {}
    public record Card(Long id, String question, String answer, int masteryLevel,
                       Instant lastReviewedAt, Instant nextReviewAt) {}
}
