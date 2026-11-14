package com.example.backend.reminder;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import com.example.backend.mail.DailyReminderRequested;
import com.example.backend.mail.MailConfiguration;
import com.example.backend.user.UserRepository;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyReminderPublisher {
    private final DailyReminderRepository reminders;
    private final UserRepository users;
    private final DailyReminderEligibility eligibility;
    private final RabbitTemplate rabbit;
    private final Clock clock;
    public DailyReminderPublisher(DailyReminderRepository reminders, UserRepository users, DailyReminderEligibility eligibility, RabbitTemplate rabbit, Clock clock) {
        this.reminders = reminders; this.users = users; this.eligibility = eligibility; this.rabbit = rabbit; this.clock = clock;
    }
    @Transactional
    public void publish(Long id) {
        var ownerId = reminders.findOwnerId(id).orElse(null);
        if (ownerId == null) return;
        var user = users.findByIdForUpdate(ownerId).orElse(null);
        if (user == null) return;
        var dispatch = reminders.findForUpdate(id).orElse(null);
        if (dispatch == null || dispatch.getStatus() != DailyReminderDispatch.Status.PENDING) return;
        if (!eligibility.canSend(user, dispatch.getReminderDate(), clock.instant())) { dispatch.skip(); return; }
        // This durable row is an outbox: publication failures roll back to PENDING for the next tick.
        try {
            var correlation = new CorrelationData();
            rabbit.convertAndSend(MailConfiguration.EXCHANGE, MailConfiguration.REMINDER_ROUTING_KEY, new DailyReminderRequested(id), message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }, correlation);
            var confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirmation.ack() || correlation.getReturned() != null) throw new IllegalStateException("Reminder was not accepted by the queue.");
            dispatch.published(clock.instant());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reminder publication interrupted.");
        } catch (Exception exception) {
            throw new IllegalStateException("Reminder publication failed.");
        }
    }
}
