package com.example.backend.admin;

import java.util.Optional;

import com.example.backend.role.Role;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminInitializerTests {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    @AfterEach
    void closeValidator() {
        validatorFactory.close();
    }

    private AdminInitializer initializer(AdminProperties properties) {
        return new AdminInitializer(properties, users, roles, encoder, validatorFactory.getValidator());
    }

    private AdminProperties validProperties() {
        return new AdminProperties("  InitialAdmin  ", "  ADMIN@EXAMPLE.COM  ", "  ExamplePassword123!  ");
    }

    @Test
    void createsVerifiedAdministratorWithBothRolesWhenNoAdminExists() {
        Role adminRole = mock(Role.class);
        Role userRole = mock(Role.class);
        when(roles.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
        when(roles.findByName(RoleName.USER)).thenReturn(Optional.of(userRole));
        when(encoder.encode("  ExamplePassword123!  ")).thenReturn("encoded-password");

        initializer(validProperties()).run();

        var captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User admin = captor.getValue();
        assertEquals("InitialAdmin", admin.getUsername());
        assertEquals("admin@example.com", admin.getEmail());
        assertEquals("encoded-password", admin.getPasswordHash());
        assertNotNull(admin.getEmailVerifiedAt());
        assertTrue(admin.isEnabled());
        assertEquals(2, admin.getRoles().size());
        assertTrue(admin.getRoles().contains(adminRole));
        assertTrue(admin.getRoles().contains(userRole));
        verify(users).existsByRoles_Name(RoleName.ADMIN);
    }

    @Test
    void existingAdministratorSkipsInitializationEvenWithoutCredentials() {
        when(users.existsByRoles_Name(RoleName.ADMIN)).thenReturn(true);

        initializer(new AdminProperties(null, null, null)).run();

        verify(users, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(roles, encoder);
    }

    @Test
    void absentCredentialsLeaveDatabaseUnchanged() {
        initializer(new AdminProperties(null, null, null)).run();

        verify(users, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(roles, encoder);
    }

    @Test
    void partialConfigurationFailsWithoutExposingPassword() {
        String password = "private-password-123";
        var properties = new AdminProperties("InitialAdmin", "", password);

        var exception = assertThrows(IllegalStateException.class, () -> initializer(properties).run());

        assertTrue(exception.getMessage().contains("ADMIN_EMAIL"));
        assertFalse(exception.getMessage().contains(password));
        assertFalse(properties.toString().contains(password));
        verify(users, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(encoder);
    }

    @Test
    void takenUsernameNeverPromotesExistingAccount() {
        when(users.existsByUsernameIgnoreCase("InitialAdmin")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> initializer(validProperties()).run());

        verify(users, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(roles, encoder);
    }

    @Test
    void takenEmailNeverPromotesExistingAccount() {
        when(users.existsByEmailIgnoreCase("admin@example.com")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> initializer(validProperties()).run());

        verify(users, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(roles, encoder);
    }

    @Test
    void passwordBeyondBcryptByteLimitDoesNotReachEncoder() {
        var properties = new AdminProperties("InitialAdmin", "admin@example.com", "\u0105".repeat(40));

        var exception = assertThrows(IllegalStateException.class, () -> initializer(properties).run());

        assertTrue(exception.getMessage().contains("72 UTF-8 bytes"));
        verifyNoInteractions(encoder);
        verify(users, never()).saveAndFlush(any(User.class));
    }
}
