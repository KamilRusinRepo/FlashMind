package com.example.backend.auth;

import java.time.Clock;

import com.example.backend.auth.dto.response.VerifyEmailResponse;
import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
    private final TokenService tokens;
    private final Clock clock;

    public EmailVerificationService(TokenService tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public VerifyEmailResponse verify(String token) {
        // Both entities are managed in this transaction: commit or roll back both changes together.
        var user = tokens.consume(token, TokenType.EMAIL_VERIFICATION);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(clock.instant());
        }
        // Verifying an address must never lift an administrator's account block.
        return new VerifyEmailResponse(true);
    }
}
