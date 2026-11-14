package com.example.backend.mail;

/** Resolve the current recipient and opt-in from the database at delivery time. */
public record DailyReminderRequested(Long reminderId) {}
