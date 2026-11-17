package com.example.backend.mail;

import java.time.Clock;
import com.example.backend.reminder.DailyReminderDispatch;
import com.example.backend.reminder.DailyReminderEligibility;
import com.example.backend.reminder.DailyReminderRepository;
import com.example.backend.user.UserRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DailyReminderMailConsumer {
    private final DailyReminderRepository reminders;
    private final UserRepository users;
    private final DailyReminderEligibility eligibility;
    private final JavaMailSender sender;
    private final MailProperties mail;
    private final Clock clock;
    public DailyReminderMailConsumer(DailyReminderRepository reminders, UserRepository users, DailyReminderEligibility eligibility,
                                     JavaMailSender sender, MailProperties mail, Clock clock) {
        this.reminders = reminders; this.users = users; this.eligibility = eligibility; this.sender = sender; this.mail = mail; this.clock = clock;
    }
    @RabbitListener(queues = MailConfiguration.REMINDER_QUEUE)
    @Transactional
    public void send(DailyReminderRequested event) {
        if (event.reminderId() == null) return;
        var ownerId = reminders.findOwnerId(event.reminderId()).orElse(null);
        if (ownerId == null) return;
        // Same lock order as scheduling, preference updates and study answers: user, then dispatch.
        var user = users.findByIdForUpdate(ownerId).orElse(null);
        if (user == null) return;
        var dispatch = reminders.findForUpdate(event.reminderId()).orElse(null);
        if (dispatch == null || dispatch.getStatus() == DailyReminderDispatch.Status.SENT || dispatch.getStatus() == DailyReminderDispatch.Status.SKIPPED) return;
        if (!eligibility.canSend(user, dispatch.getReminderDate(), clock.instant())) { dispatch.skip(); return; }
        var message = new SimpleMailMessage();
        message.setFrom(mail.from());
        message.setTo(user.getEmail());
        message.setSubject("Time for a little practice with FlashMind");
        message.setText("Your flashcards are ready for today's practice. A short session can make a lasting difference.\n\n"
                + mail.verificationUrl().resolve("dashboard")
                + "\n\nYou receive this reminder because Daily Reminders are enabled for your account."
                + " You can turn them off in Settings: " + mail.verificationUrl().resolve("settings"));
        sender.send(message);
        dispatch.sent(clock.instant());
    }
}
