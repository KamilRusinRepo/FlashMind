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

class PasswordResetMailConsumerTests {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final PasswordResetMailConsumer consumer = new PasswordResetMailConsumer(sender,
            new MailProperties("no-reply@flashmind.local", URI.create("http://localhost:3000/verify-email")),
            new PasswordResetMailProperties(URI.create("http://localhost:3000/reset-password")), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void sendsResetLinkToOwnerWithExpirationAndDoesNotExposePayloadInToString() {
        var event = new PasswordResetEmailRequested(1L, "learner@example.invalid", "a".repeat(43), NOW.plusSeconds(1800));
        consumer.send(event);
        var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        assertArrayEquals(new String[]{"learner@example.invalid"}, captured.getValue().getTo());
        assertEquals("Reset your FlashMind password", captured.getValue().getSubject());
        assertTrue(captured.getValue().getText().contains("http://localhost:3000/reset-password?token=" + event.token()));
        assertTrue(captured.getValue().getText().contains(event.expiresAt().toString()));
        assertFalse(event.toString().contains(event.token()));
        assertFalse(event.toString().contains(event.recipient()));
    }

    @Test
    void expiredMessagesAreAcknowledgedWithoutSending() {
        consumer.send(new PasswordResetEmailRequested(1L, "learner@example.invalid", "a".repeat(43), NOW));
        verifyNoInteractions(sender);
    }
}
