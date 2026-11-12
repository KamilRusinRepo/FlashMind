package com.example.backend.auth;

import com.example.backend.auth.dto.request.RegisterRequest;
import com.example.backend.auth.dto.response.RegisterResponse;
import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.mail.VerificationEmailRequested;
import com.example.backend.role.Role;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final ApplicationEventPublisher events;

    public RegistrationService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                               TokenService tokens, ApplicationEventPublisher events) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.events = events;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (users.existsByUsernameIgnoreCase(request.username())) {
            throw new RegistrationConflictException("username", "This username is already taken.");
        }
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new RegistrationConflictException("email", "This email address is already registered.");
        }

        Role userRole = roles.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("The USER role is missing. Check database migrations."));
        User user = new User(request.username(), request.email(), passwordEncoder.encode(request.password()));
        user.addRole(userRole);
        // Flush inside this transaction so database uniqueness checks also cover concurrent registrations.
        User saved = users.saveAndFlush(user);
        var token = tokens.issue(saved.getId(), TokenType.EMAIL_VERIFICATION);
        events.publishEvent(new VerificationEmailRequested(saved.getId(), saved.getEmail(),
                token.value(), token.expiresAt()));

        return new RegisterResponse(saved.getId(), saved.getUsername(), saved.getEmail(),
                saved.getEmailVerifiedAt() != null, saved.getCreatedAt());
    }
}
