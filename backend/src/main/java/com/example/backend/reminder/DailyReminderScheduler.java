package com.example.backend.reminder;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class DailyReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(DailyReminderScheduler.class);
    private final ReminderProperties properties;
    private final Clock clock;
    private final DailyReminderRepository reminders;
    private final DailyReminderService service;
    private final DailyReminderPublisher publisher;
    public DailyReminderScheduler(ReminderProperties properties, Clock clock, DailyReminderRepository reminders,
                                   DailyReminderService service, DailyReminderPublisher publisher) {
        this.properties = properties; this.clock = clock; this.reminders = reminders; this.service = service; this.publisher = publisher;
    }
    @Scheduled(fixedDelayString = "${app.reminders.poll-delay-ms:300000}", initialDelayString = "10000")
    public void tick() {
        var now = clock.instant();
        var local = now.atZone(properties.zoneId());
        if (local.getHour() < properties.sendHour()) return;
        var date = local.toLocalDate();
        var start = date.atStartOfDay(properties.zoneId()).toInstant();
        var end = date.plusDays(1).atStartOfDay(properties.zoneId()).toInstant();
        for (Long userId : reminders.findCandidateIds(date, start, end, now, PageRequest.of(0, 100))) {
            try { service.prepare(userId, date, now); }
            catch (RuntimeException exception) { log.warn("Daily reminder preparation failed for user {}.", userId); }
        }
        for (Long id : reminders.findPendingIds(DailyReminderDispatch.Status.PENDING, PageRequest.of(0, 50))) {
            try { publisher.publish(id); }
            catch (RuntimeException exception) { log.warn("Daily reminder publication failed for dispatch {}; a later tick will retry.", id); }
        }
    }
}
