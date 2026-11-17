package com.example.backend.user;

import com.example.backend.auth.SessionUser;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.user.dto.request.ChangePasswordRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordChangeService {
    private final UserRepository users;
    private final UserTokenRepository tokens;
    private final PasswordEncoder encoder;
    public PasswordChangeService(UserRepository users, UserTokenRepository tokens, PasswordEncoder encoder) {
        this.users = users; this.tokens = tokens; this.encoder = encoder;
    }

    @Transactional
    public void change(SessionUser principal, ChangePasswordRequest request) {
        // Serialize account mutations; recheck session version after acquiring the lock.
        var user = users.findByIdForUpdate(principal.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getSessionVersion() != principal.sessionVersion()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!user.isEnabled() || user.getEmailVerifiedAt() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new PasswordChangeException("currentPassword", "The current password is incorrect.");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new PasswordChangeException("confirmPassword", "Passwords must match.");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.invalidateSessions();
        // Previously issued recovery and email change links must no longer modify the account.
        tokens.deleteUnusedByUserIdAndType(user.getId(), TokenType.EMAIL_CHANGE);
        tokens.deleteUnusedByUserIdAndType(user.getId(), TokenType.PASSWORD_RESET);
        users.saveAndFlush(user);
    }
}
