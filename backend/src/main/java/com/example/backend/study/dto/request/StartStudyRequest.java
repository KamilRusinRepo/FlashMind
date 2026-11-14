package com.example.backend.study.dto.request;

import com.example.backend.study.StudyMode;
import jakarta.validation.constraints.NotNull;

public record StartStudyRequest(@NotNull StudyMode mode) {}
