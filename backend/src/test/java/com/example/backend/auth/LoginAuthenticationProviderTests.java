package com.example.backend.auth;

import java.time.Instant;
import java.util.Optional;

import com.example.backend.role.Role;
import com.example.backend.role.RoleName;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAuthenticationProviderTests {
    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final LoginAuthenticationProvider provider = new LoginAuthenticationProvider(users, encoder);
    private final String password = "  CorrectPassword123!  ";

    @Test
    void authenticatesWithRealPasswordHashAndStoresOnlySafePrincipalAndAuthorities() {
        User account = account(true, true);
        var role = mock(Role.class);
        when(role.getName()).thenReturn(RoleName.USER);
        account.addRole(role);
        when(users.findByUsernameIgnoreCase("Learner")).thenReturn(Optional.of(account));
        var result = provider.authenticate(request(" Learner ", password));
        assertTrue(result.isAuthenticated());
        assertNull(result.getCredentials());
        var principal = (SessionUser) result.getPrincipal();
        assertEquals("Learner", principal.getName());
        assertEquals(java.util.List.of("USER"), principal.roles());
        assertTrue(result.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_USER")));
        assertFalse(principal.toString().contains(password));
        assertFalse(principal.toString().contains(account.getPasswordHash()));
    }

    @Test
    void acceptsEmailAndPreservesPasswordWhitespace() {
        when(users.findByEmailIgnoreCase("LEARNER@example.com")).thenReturn(Optional.of(account(true, true)));
        assertTrue(provider.authenticate(request(" LEARNER@example.com ", password)).isAuthenticated());
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(request("LEARNER@example.com", password.trim())));
    }

    @Test
    void unknownAccountAndWrongPasswordHaveTheSameFailure() {
        when(users.findByUsernameIgnoreCase("Learner")).thenReturn(Optional.of(account(true, true)));
        var unknown = assertThrows(BadCredentialsException.class, () -> provider.authenticate(request("unknown", password)));
        var wrong = assertThrows(BadCredentialsException.class, () -> provider.authenticate(request("Learner", "wrong")));
        assertEquals(unknown.getMessage(), wrong.getMessage());
    }

    @Test
    void revealsUnverifiedEmailOnlyAfterCorrectPassword() {
        when(users.findByUsernameIgnoreCase("Learner")).thenReturn(Optional.of(account(true, false)));
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(request("Learner", "wrong")));
        assertThrows(EmailNotVerifiedException.class, () -> provider.authenticate(request("Learner", password)));
    }

    @Test
    void blockedAccountNeverAuthenticatesOrRevealsVerificationStatus() {
        when(users.findByUsernameIgnoreCase("Learner")).thenReturn(Optional.of(account(false, false)));
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(request("Learner", password)));
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(request("Learner", "wrong")));
    }

    @Test
    void loginRequestRedactsBothCredentialsAndOnlyTrimsTheLogin() {
        var dto = new com.example.backend.auth.dto.request.LoginRequest(" Learner ", password);
        assertEquals("Learner", dto.login());
        assertEquals(password, dto.password());
        assertFalse(dto.toString().contains("Learner"));
        assertFalse(dto.toString().contains(password));
    }

    private User account(boolean enabled, boolean verified) {
        var user = new User("Learner", "learner@example.com", encoder.encode(password));
        user.setEnabled(enabled);
        if (verified) user.setEmailVerifiedAt(Instant.now());
        return user;
    }

    private UsernamePasswordAuthenticationToken request(String login, String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated(login, password);
    }
}
