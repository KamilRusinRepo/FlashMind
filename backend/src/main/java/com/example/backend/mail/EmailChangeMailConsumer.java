package com.example.backend.mail;

import java.time.Clock;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EmailChangeMailConsumer {
    private final JavaMailSender sender;
    private final MailProperties mail;
    private final Clock clock;
    public EmailChangeMailConsumer(JavaMailSender sender, MailProperties mail, Clock clock) {
        this.sender = sender; this.mail = mail; this.clock = clock;
    }

    @RabbitListener(queues = MailConfiguration.CHANGE_QUEUE)
    public void send(EmailChangeRequested event) {
        if (event.expiresAt() == null || !event.expiresAt().isAfter(clock.instant())) return;
        // The change confirmation is a sibling of the configured verification page.
        String link = UriComponentsBuilder.fromUri(mail.verificationUrl().resolve("confirm-email-change"))
                .queryParam("token", event.token()).build().encode().toUriString();
        var message = new SimpleMailMessage();
        message.setFrom(mail.from());
        message.setTo(event.recipient());
        message.setSubject("Confirm your new FlashMind email address");
        message.setText("Confirm the new email address for your FlashMind account:\n\n" + link
                + "\n\nYour current email address remains active until you confirm this change."
                + "\nAfter confirmation, sign in again using your new email address or username."
                + "\nThis link expires at " + event.expiresAt()
                + ". Use the latest confirmation email. If you did not request this change, ignore this message.");
        sender.send(message);
    }
}
