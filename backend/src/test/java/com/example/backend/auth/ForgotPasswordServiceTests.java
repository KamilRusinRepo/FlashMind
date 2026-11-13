package com.example.backend.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserToken;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.mail.PasswordResetEmailRequested;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.*;

class ForgotPasswordServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private final UserRepository users = mock(UserRepository.class);
    private final UserTokenRepository tokens = mock(UserTokenRepository.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ForgotPasswordService service = new ForgotPasswordService(users, tokens, tokenService, events, Clock.fixed(NOW, ZoneOffset.UTC));

    private void eligibleAccount() {
        var user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("learner@example.invalid");
        when(user.isEnabled()).thenReturn(true);
        when(user.getEmailVerifiedAt()).thenReturn(NOW.minusSeconds(3600));
        when(users.findByEmailForUpdate("learner@example.invalid")).thenReturn(Optional.of(user));
        when(tokens.findFirstByUser_IdAndTypeOrderByCreatedAtDesc(1L, TokenType.PASSWORD_RESET)).thenReturn(Optional.empty());
        when(tokenService.issue(1L, TokenType.PASSWORD_RESET)).thenReturn(new TokenService.IssuedToken("a".repeat(43), NOW.plusSeconds(1800)));
    }

    @Test
    void replacesOnlyResetTokensThenRegistersMailEventForEligibleAccount() {
        eligibleAccount();
        service.requestReset("learner@example.invalid");
        var order = inOrder(tokens, tokenService, events);
        order.verify(tokens).deleteUnusedByUserIdAndType(1L, TokenType.PASSWORD_RESET);
        order.verify(tokenService).issue(1L, TokenType.PASSWORD_RESET);
        order.verify(events).publishEvent(new PasswordResetEmailRequested(1L, "learner@example.invalid", "a".repeat(43), NOW.plusSeconds(1800)));
        verify(tokens, never()).deleteUnusedByUserIdAndType(anyLong(), eq(TokenType.EMAIL_VERIFICATION));
    }

    @Test
    void missingBlockedAndUnverifiedAccountsDoNotIssueTokensOrMail() {
        when(users.findByEmailForUpdate("unknown@example.invalid")).thenReturn(Optional.empty());
        var blocked = mock(User.class);
        when(users.findByEmailForUpdate("blocked@example.invalid")).thenReturn(Optional.of(blocked));
        var unverified = mock(User.class);
        when(unverified.isEnabled()).thenReturn(true);
        when(users.findByEmailForUpdate("unverified@example.invalid")).thenReturn(Optional.of(unverified));
        service.requestReset("unknown@example.invalid");
        service.requestReset("blocked@example.invalid");
        service.requestReset("unverified@example.invalid");
        verifyNoInteractions(tokens, tokenService, events);
    }

    @Test
    void cooldownPreservesOldTokensUntilExactlySixtySeconds() {
        eligibleAccount();
        var latest = mock(UserToken.class);
        when(tokens.findFirstByUser_IdAndTypeOrderByCreatedAtDesc(1L, TokenType.PASSWORD_RESET)).thenReturn(Optional.of(latest));
        when(latest.getCreatedAt()).thenReturn(NOW.minusSeconds(60).plusMillis(1));
        service.requestReset("learner@example.invalid");
        verifyNoInteractions(tokenService, events);
        verify(tokens, never()).deleteUnusedByUserIdAndType(anyLong(), any());

        when(latest.getCreatedAt()).thenReturn(NOW.minusSeconds(60));
        service.requestReset("learner@example.invalid");
        verify(tokenService).issue(1L, TokenType.PASSWORD_RESET);
        verify(events).publishEvent(any(PasswordResetEmailRequested.class));
    }
}
