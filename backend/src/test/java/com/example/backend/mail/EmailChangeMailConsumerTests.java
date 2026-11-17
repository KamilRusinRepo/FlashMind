package com.example.backend.mail;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailChangeMailConsumerTests {
    @Test void sendsLinkToNewAddressWithoutExposingPayloadAndSkipsExpiredMessages() {
        var now = Instant.parse("2026-11-14T16:00:00Z");
        var sender = mock(JavaMailSender.class);
        var consumer = new EmailChangeMailConsumer(sender, new MailProperties("no-reply@flashmind.local", URI.create("http://localhost:3000/verify-email")), Clock.fixed(now, ZoneOffset.UTC));
        var event = new EmailChangeRequested(1L, "new@example.invalid", "a".repeat(43), now.plusSeconds(3600));
        consumer.send(event);
        var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        assertArrayEquals(new String[]{event.recipient()}, captured.getValue().getTo());
        assertTrue(captured.getValue().getText().contains("http://localhost:3000/confirm-email-change?token=" + event.token()));
        assertFalse(event.toString().contains(event.token()));
        assertFalse(event.toString().contains(event.recipient()));
        clearInvocations(sender);
        consumer.send(new EmailChangeRequested(1L, event.recipient(), event.token(), now));
        verifyNoInteractions(sender);
    }
}
