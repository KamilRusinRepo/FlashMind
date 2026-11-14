package com.example.backend.user;

import java.time.Clock;
import java.time.Duration;
import com.example.backend.auth.token.InvalidTokenException;
import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserToken;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.mail.EmailChangeRequested;
import com.example.backend.user.dto.request.RequestEmailChange;
import com.example.backend.user.dto.response.PendingEmailChangeResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailChangeService {
    private final UserRepository users;
    private final UserTokenRepository storedTokens;
    private final TokenService tokens;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public EmailChangeService(UserRepository users, UserTokenRepository storedTokens, TokenService tokens,
                              PasswordEncoder encoder, ApplicationEventPublisher events, Clock clock) {
        this.users = users; this.storedTokens = storedTokens; this.tokens = tokens;
        this.encoder = encoder; this.events = events; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PendingEmailChangeResponse pending(Long userId) {
        requireEnabled(users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
        var latest = storedTokens.findFirstByUser_IdAndTypeOrderByCreatedAtDesc(userId, TokenType.EMAIL_CHANGE).orElse(null);
        boolean active = latest != null && latest.getUsedAt() == null && latest.getExpiresAt().isAfter(clock.instant());
        return new PendingEmailChangeResponse(active ? latest.getTargetEmail() : null, active ? latest.getExpiresAt() : null, cooldown(latest));
    }

    @Transactional
    public PendingEmailChangeResponse request(Long userId, RequestEmailChange request) {
        var user = requireEnabled(users.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new EmailChangeException(HttpStatus.BAD_REQUEST, "currentPassword", "The current password is incorrect.");
        }
        if (request.email().equalsIgnoreCase(user.getEmail())) {
            throw new EmailChangeException(HttpStatus.BAD_REQUEST, "email", "Enter a different email address.");
        }
        requireAvailable(request.email());
        var latest = storedTokens.findFirstByUser_IdAndTypeOrderByCreatedAtDesc(userId, TokenType.EMAIL_CHANGE).orElse(null);
        if (cooldown(latest) > 0) throw new EmailChangeException(HttpStatus.TOO_MANY_REQUESTS, null, "Wait 60 seconds before requesting another email change.");
        storedTokens.deleteUnusedByUserIdAndType(userId, TokenType.EMAIL_CHANGE);
        var issued = tokens.issueEmailChange(userId, request.email());
        events.publishEvent(new EmailChangeRequested(userId, request.email(), issued.value(), issued.expiresAt()));
        return new PendingEmailChangeResponse(request.email(), issued.expiresAt(), 60);
    }

    @Transactional
    public void confirm(String value) {
        var token = tokens.consumeEmailChange(value);
        var user = token.getUser();
        if (!user.isEnabled() || user.getEmailVerifiedAt() == null) throw new InvalidTokenException();
        requireAvailable(token.getTargetEmail());
        user.setEmail(token.getTargetEmail());
        user.setEmailVerifiedAt(clock.instant());
        user.invalidateSessions();
        try { users.saveAndFlush(user); }
        catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint && "uq_users_email".equals(constraint.getConstraintName())) {
                    throw new EmailChangeException(HttpStatus.CONFLICT, "email", "This email address is no longer available. Request another change in Settings.");
                }
            }
            throw exception;
        }
        // Links delivered to the former address must no longer reset or verify this account.
        for (var type : TokenType.values()) storedTokens.deleteUnusedByUserIdAndType(user.getId(), type);
    }

    private void requireAvailable(String email) {
        if (users.existsByEmailIgnoreCase(email)) throw new EmailChangeException(HttpStatus.CONFLICT, "email", "This email address is already registered.");
    }

    private User requireEnabled(User user) {
        if (!user.isEnabled() || user.getEmailVerifiedAt() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }

    private int cooldown(UserToken latest) {
        if (latest == null) return 0;
        return (int) Math.min(60, Math.max(0, (Duration.between(clock.instant(), latest.getCreatedAt().plusSeconds(60)).toMillis() + 999) / 1000));
    }
}
