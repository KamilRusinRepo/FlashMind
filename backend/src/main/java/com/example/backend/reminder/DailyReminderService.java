package com.example.backend.reminder;

import java.time.Instant;
import java.time.LocalDate;
import com.example.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyReminderService {
    private final UserRepository users;
    private final DailyReminderRepository reminders;
    private final DailyReminderEligibility eligibility;
    public DailyReminderService(UserRepository users, DailyReminderRepository reminders, DailyReminderEligibility eligibility) {
        this.users = users; this.reminders = reminders; this.eligibility = eligibility;
    }
    @Transactional
    public void prepare(Long userId, LocalDate date, Instant now) {
        var user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null || reminders.existsByUser_IdAndReminderDate(userId, date) || !eligibility.canSend(user, date, now)) return;
        reminders.saveAndFlush(new DailyReminderDispatch(user, date, now));
    }
}
