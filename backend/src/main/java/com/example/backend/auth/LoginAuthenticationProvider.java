package com.example.backend.auth;

import com.example.backend.user.UserRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginAuthenticationProvider implements AuthenticationProvider {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String missingUserHash;

    public LoginAuthenticationProvider(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
        // Unknown accounts still perform a password hash check; this is not an account password.
        this.missingUserHash = encoder.encode("missing-account-placeholder");
    }

    @Override
    @Transactional(readOnly = true)
    public Authentication authenticate(Authentication authentication) {
        String login = authentication.getName().trim();
        String password = authentication.getCredentials() instanceof String value ? value : "";
        var found = login.contains("@") ? users.findByEmailIgnoreCase(login) : users.findByUsernameIgnoreCase(login);
        boolean passwordMatches = encoder.matches(password, found.isPresent() ? found.get().getPasswordHash() : missingUserHash);
        if (found.isEmpty() || !passwordMatches || !found.get().isEnabled()) {
            throw new BadCredentialsException("Invalid email/username or password.");
        }
        var user = found.get();
        // Reveal verification status only after a valid password, and never for a blocked account.
        if (user.getEmailVerifiedAt() == null) throw new EmailNotVerifiedException(user.getEmail());
        var roles = user.getRoles().stream().map(role -> role.getName().name()).sorted().toList();
        var authorities = roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        var principal = new SessionUser(user.getId(), user.getUsername(), user.getEmail(), roles, user.getSessionVersion());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
