package com.example.backend.auth;

import com.example.backend.auth.token.InvalidTokenException;
import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserTokenRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResetPasswordService {
    private final TokenService tokens;
    private final UserTokenRepository storedTokens;
    private final PasswordEncoder encoder;

    public ResetPasswordService(TokenService tokens, UserTokenRepository storedTokens, PasswordEncoder encoder) {
        this.tokens = tokens;
        this.storedTokens = storedTokens;
        this.encoder = encoder;
    }

    @Transactional
    public void reset(String token, String password) {
        // consume locks user then token. All changes, including token use, share this transaction.
        var user = tokens.consume(token, TokenType.PASSWORD_RESET);
        if (!user.isEnabled() || user.getEmailVerifiedAt() == null) throw new InvalidTokenException();
        user.setPasswordHash(encoder.encode(password));
        user.invalidateSessions();
        storedTokens.deleteUnusedByUserIdAndType(user.getId(), TokenType.PASSWORD_RESET);
    }
}
