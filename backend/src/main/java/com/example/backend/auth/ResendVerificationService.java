package com.example.backend.auth;

import java.time.Clock;
import java.time.Duration;

import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.mail.VerificationEmailRequested;
import com.example.backend.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResendVerificationService {
    public static final int COOLDOWN_SECONDS = 60;
    private final UserRepository users;
    private final UserTokenRepository tokens;
    private final TokenService tokenService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ResendVerificationService(UserRepository users, UserTokenRepository tokens, TokenService tokenService,
                                     ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.tokenService = tokenService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void resend(String email) {
        var user = users.findByEmailForUpdate(email).orElse(null);
        if (user == null || !user.isEnabled() || user.getEmailVerifiedAt() != null) {
            return;
        }
        var latest = tokens.findFirstByUser_IdAndTypeOrderByCreatedAtDesc(user.getId(), TokenType.EMAIL_VERIFICATION);
        if (latest.isPresent() && clock.instant().isBefore(
                latest.get().getCreatedAt().plus(Duration.ofSeconds(COOLDOWN_SECONDS)))) {
            return;
        }
        // Delete only unused verification tokens. Used history and password-reset tokens are untouched.
        // Deletion, issuance and event registration share the transaction, so failure restores old links.
        tokens.deleteUnusedByUserIdAndType(user.getId(), TokenType.EMAIL_VERIFICATION);
        var issued = tokenService.issue(user.getId(), TokenType.EMAIL_VERIFICATION);
        events.publishEvent(new VerificationEmailRequested(user.getId(), user.getEmail(), issued.value(), issued.expiresAt()));
    }
}
