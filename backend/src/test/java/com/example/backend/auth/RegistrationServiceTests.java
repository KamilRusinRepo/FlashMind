package com.example.backend.auth;

import java.util.Optional;

import com.example.backend.auth.dto.request.RegisterRequest;
import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.mail.VerificationEmailRequested;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import com.example.backend.role.Role;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationServiceTests {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final Role userRole = mock(Role.class);
    private final TokenService tokens = mock(TokenService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RegistrationService service = new RegistrationService(users, roles, encoder, tokens, events);

    @BeforeEach
    void setUp() {
        when(roles.findByName(RoleName.USER)).thenReturn(Optional.of(userRole));
        when(tokens.issue(any(), any())).thenReturn(new TokenService.IssuedToken("test-token", Instant.now().plusSeconds(3600)));
    }

    @Test
    void registersNormalizedUnverifiedUserWithOnlyUserRoleAndHashedPassword() {
        var request = new RegisterRequest("  NewLearner  ", "  LEARNER@EXAMPLE.COM  ", "  Example123!  ");
        when(encoder.encode(request.password())).thenReturn("encoded-password");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.register(request);

        var captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertEquals("NewLearner", saved.getUsername());
        assertEquals("learner@example.com", saved.getEmail());
        assertEquals("encoded-password", saved.getPasswordHash());
        assertEquals(1, saved.getRoles().size());
        assertTrue(saved.getRoles().contains(userRole));
        assertTrue(saved.isEnabled());
        assertNull(saved.getEmailVerifiedAt());
        assertFalse(response.emailVerified());
        verify(encoder).encode("  Example123!  ");
        verify(roles, never()).findByName(RoleName.ADMIN);
        verify(tokens).issue(saved.getId(), TokenType.EMAIL_VERIFICATION);
        verify(events).publishEvent(any(VerificationEmailRequested.class));
    }

    @Test
    void rejectsTakenUsernameWithoutHashingOrSaving() {
        when(users.existsByUsernameIgnoreCase("Learner")).thenReturn(true);

        var exception = assertThrows(RegistrationConflictException.class, () ->
                service.register(new RegisterRequest("Learner", "learner@example.com", "Example123!")));

        assertEquals("username", exception.getField());
        verifyNoInteractions(encoder);
        verifyNoInteractions(tokens, events);
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void rejectsTakenEmailWithoutHashingOrSaving() {
        when(users.existsByEmailIgnoreCase("learner@example.com")).thenReturn(true);

        var exception = assertThrows(RegistrationConflictException.class, () ->
                service.register(new RegisterRequest("Learner", "LEARNER@EXAMPLE.COM", "Example123!")));

        assertEquals("email", exception.getField());
        verifyNoInteractions(encoder);
        verifyNoInteractions(tokens, events);
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void missingSeededRoleDoesNotCreateAnAccount() {
        when(roles.findByName(RoleName.USER)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
                service.register(new RegisterRequest("Learner", "learner@example.com", "Example123!")));

        verifyNoInteractions(encoder);
        verifyNoInteractions(tokens, events);
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void databaseUniquenessConflictPropagatesToGlobalHandler() {
        when(encoder.encode(any())).thenReturn("encoded-password");
        when(users.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Concurrent account creation"));

        assertThrows(DataIntegrityViolationException.class, () ->
                service.register(new RegisterRequest("Learner", "learner@example.com", "Example123!")));
        verifyNoInteractions(tokens, events);
    }
}
