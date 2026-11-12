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
public class VerificationMailPublisher {
    private static final Logger log = LoggerFactory.getLogger(VerificationMailPublisher.class);
    private final RabbitTemplate rabbit;

    public VerificationMailPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterRegistrationCommitted(VerificationEmailRequested event) {
        try {
            var correlation = new CorrelationData();
            rabbit.convertAndSend(MailConfiguration.EXCHANGE, MailConfiguration.ROUTING_KEY, event, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }, correlation);
            var confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirmation.ack() || correlation.getReturned() != null) {
                throw new IllegalStateException("Mail message was not accepted by its queue.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure(event.userId());
        } catch (Exception exception) {
            // Account is already committed. Do not turn successful registration into a misleading HTTP 500.
            logFailure(event.userId());
        }
    }

    private void logFailure(Long userId) {
        log.error("Verification email publication failed for user {}. Account exists; email requires resending.", userId);
    }
}
