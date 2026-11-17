package com.example.backend.admin.dto.request;

import jakarta.validation.constraints.NotNull;

public record SetUserEnabledRequest(@NotNull Boolean enabled) {}
