package com.example.backend.study.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitStudyAnswerRequest(
        @NotNull(message = "Provide an answer, or an empty string to skip.")
        @Size(max = 2000, message = "Use at most 2000 characters.") String userAnswer,
        Boolean skipped
) {
    public SubmitStudyAnswerRequest { skipped = Boolean.TRUE.equals(skipped); }
    @Override public String toString() { return "SubmitStudyAnswerRequest[content=REDACTED]"; }
}
