package com.example.backend.mail;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetMailPublisher {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailPublisher.class);
    private final RabbitTemplate rabbit;

    public PasswordResetMailPublisher(RabbitTemplate rabbit) { this.rabbit = rabbit; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterResetRequested(PasswordResetEmailRequested event) {
        try {
            var correlation = new CorrelationData();
            rabbit.convertAndSend(MailConfiguration.EXCHANGE, MailConfiguration.RESET_ROUTING_KEY, event, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }, correlation);
            var confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirmation.ack() || correlation.getReturned() != null) {
                throw new IllegalStateException("Reset message was not accepted by its queue.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure(event.userId());
        } catch (Exception exception) {
            // Preserve the neutral API result even if publication fails after the DB commit.
            logFailure(event.userId());
        }
    }

    private void logFailure(Long userId) {
        log.error("Password reset email publication failed for user {}. Another reset request is required.", userId);
    }
}
