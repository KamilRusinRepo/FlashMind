package com.example.backend.reminder;

import java.time.ZoneId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.reminders")
public record ReminderProperties(@Min(0) @Max(23) int sendHour, String zone, @Min(1000) long pollDelayMs) {
    public ReminderProperties {
        zone = zone == null ? "Europe/Warsaw" : zone;
        ZoneId.of(zone);
    }
    public ZoneId zoneId() { return ZoneId.of(zone); }
}
