package com.example.backend.reminder;

import java.time.Instant;
import java.time.LocalDate;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.study.StudyAnswerRepository;
import com.example.backend.user.User;
import org.springframework.stereotype.Component;

@Component
public class DailyReminderEligibility {
    private final ReminderProperties properties;
    private final FlashcardRepository cards;
    private final StudyAnswerRepository answers;
    public DailyReminderEligibility(ReminderProperties properties, FlashcardRepository cards, StudyAnswerRepository answers) {
        this.properties = properties; this.cards = cards; this.answers = answers;
    }
    public boolean canSend(User user, LocalDate date, Instant now) {
        var local = now.atZone(properties.zoneId());
        if (!date.equals(local.toLocalDate()) || local.getHour() < properties.sendHour() || !user.isDailyRemindersEnabled()
                || !user.isEnabled() || user.getEmailVerifiedAt() == null) return false;
        var start = date.atStartOfDay(properties.zoneId()).toInstant();
        var end = date.plusDays(1).atStartOfDay(properties.zoneId()).toInstant();
        return !answers.existsBySession_User_IdAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(user.getId(), start, end)
                && cards.hasCardsForReview(user.getId(), now);
    }
}
