package com.example.backend.reminder.dto.response;

public record ReminderSettingsResponse(boolean enabled, int sendHour, String timezone) {}
