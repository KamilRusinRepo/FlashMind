package com.example.backend.mail;

import java.time.Clock;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class VerificationMailConsumer {
    private final JavaMailSender sender;
    private final MailProperties properties;
    private final Clock clock;

    public VerificationMailConsumer(JavaMailSender sender, MailProperties properties, Clock clock) {
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @RabbitListener(queues = MailConfiguration.QUEUE)
    public void send(VerificationEmailRequested event) {
        if (event.expiresAt() == null || !event.expiresAt().isAfter(clock.instant())) {
            return; // An expired verification link is no longer useful; acknowledge without sending.
        }
        String link = UriComponentsBuilder.fromUri(properties.verificationUrl())
                .queryParam("token", event.token()).build().encode().toUriString();
        var message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(event.recipient());
        message.setSubject("Confirm your FlashMind email address");
        message.setText("Welcome to FlashMind!\n\nConfirm your email address:\n" + link
                + "\n\nThis link expires at " + event.expiresAt()
                + ".\nIf you did not create this account, you can ignore this email.");
        sender.send(message);
    }
}
