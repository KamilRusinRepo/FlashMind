package com.example.backend.reminder.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateRemindersRequest(@NotNull Boolean enabled) {}
