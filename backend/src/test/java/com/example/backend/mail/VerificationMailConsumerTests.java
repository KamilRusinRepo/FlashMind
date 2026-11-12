package com.example.backend.mail;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationMailConsumerTests {
    private final Instant now = Instant.parse("2026-11-12T10:00:00Z");
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final VerificationMailConsumer consumer = new VerificationMailConsumer(sender,
            new MailProperties("no-reply@flashmind.local", URI.create("http://localhost:3000/verify-email")),
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void sendsLinkToAccountOwnerWithExpiryAndConfiguredSender() {
        consumer.send(event(now.plusSeconds(3600)));
        var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        var mail = captured.getValue();
        assertArrayEquals(new String[]{"learner@example.com"}, mail.getTo());
        assertEquals("no-reply@flashmind.local", mail.getFrom());
        assertEquals("Confirm your FlashMind email address", mail.getSubject());
        assertTrue(mail.getText().contains("http://localhost:3000/verify-email?token=" + "a".repeat(43)));
        assertTrue(mail.getText().contains(now.plusSeconds(3600).toString()));
    }

    @Test
    void neverSendsAnExpiredLink() {
        consumer.send(event(now));
        consumer.send(event(now.minusSeconds(1)));
        verifyNoInteractions(sender);
    }

    @Test
    void propagatesSmtpFailureSoTheListenerCanRetry() {
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        assertThrows(MailSendException.class, () -> consumer.send(event(now.plusSeconds(3600))));
    }

    @Test
    void internalMessageDoesNotExposeTokenOrAddressInToString() {
        String text = event(now.plusSeconds(3600)).toString();
        assertFalse(text.contains("a".repeat(43)));
        assertFalse(text.contains("learner@example.com"));
    }

    @Test
    void rejectsVerificationUrlsWithAnEmbeddedQueryOrUnsupportedScheme() {
        assertThrows(IllegalArgumentException.class, () -> new MailProperties("from@example.com", URI.create("javascript:alert(1)")));
        assertThrows(IllegalArgumentException.class, () -> new MailProperties("from@example.com", URI.create("https://example.com/verify?token=old")));
    }

    private VerificationEmailRequested event(Instant expiresAt) {
        return new VerificationEmailRequested(1L, "learner@example.com", "a".repeat(43), expiresAt);
    }
}
