package com.example.backend.mail;

import java.time.Clock;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PasswordResetMailConsumer {
    private final JavaMailSender sender;
    private final MailProperties mail;
    private final PasswordResetMailProperties reset;
    private final Clock clock;

    public PasswordResetMailConsumer(JavaMailSender sender, MailProperties mail, PasswordResetMailProperties reset, Clock clock) {
        this.sender = sender;
        this.mail = mail;
        this.reset = reset;
        this.clock = clock;
    }

    @RabbitListener(queues = MailConfiguration.RESET_QUEUE)
    public void send(PasswordResetEmailRequested event) {
        if (event.expiresAt() == null || !event.expiresAt().isAfter(clock.instant())) return;

        String link = UriComponentsBuilder.fromUri(reset.passwordResetUrl())
                .queryParam("token", event.token()).build().encode().toUriString();
        var message = new SimpleMailMessage();
        message.setFrom(mail.from());
        message.setTo(event.recipient());
        message.setSubject("Reset your FlashMind password");
        message.setText("We received a request to reset your FlashMind password.\n\nChoose a new password:\n" + link
                + "\n\nThis link expires at " + event.expiresAt()
                + ".\nUse the latest reset email. If you did not request this change, ignore this email. Your password has not been changed.");
        sender.send(message);
    }
}
