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
public class EmailChangeMailPublisher {
    private static final Logger log = LoggerFactory.getLogger(EmailChangeMailPublisher.class);
    private final RabbitTemplate rabbit;
    public EmailChangeMailPublisher(RabbitTemplate rabbit) { this.rabbit = rabbit; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(EmailChangeRequested event) {
        try {
            var correlation = new CorrelationData();
            rabbit.convertAndSend(MailConfiguration.EXCHANGE, MailConfiguration.CHANGE_ROUTING_KEY, event, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }, correlation);
            var confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirmation.ack() || correlation.getReturned() != null) throw new IllegalStateException("Email change message was not accepted.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure(event.userId());
        } catch (Exception exception) {
            logFailure(event.userId());
        }
    }

    private void logFailure(Long userId) {
        // Never log a message, SMTP exception, token or recipient.
        log.error("Email change publication failed for user {}. Request another confirmation from Settings.", userId);
    }
}
