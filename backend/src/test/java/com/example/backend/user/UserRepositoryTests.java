package com.example.backend.user;

import com.example.backend.support.PostgresIntegrationTest;

import java.time.Instant;

import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class UserRepositoryTests extends PostgresIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsUserWithBothRolesAndLoadsAccountIgnoringCase() {
        User user = new User("PersistenceTest", "persistence@example.invalid", "test-password-hash");
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        user.addRole(roles.findByName(RoleName.ADMIN).orElseThrow());
        Long id = users.saveAndFlush(user).getId();
        entityManager.clear();

        User loaded = users.findByEmailIgnoreCase("PERSISTENCE@EXAMPLE.INVALID").orElseThrow();

        assertEquals(id, loaded.getId());
        assertEquals("test-password-hash", loaded.getPasswordHash());
        assertNotNull(loaded.getCreatedAt());
        assertTrue(loaded.isEnabled());
        assertNull(loaded.getEmailVerifiedAt());
        assertEquals(2, loaded.getRoles().size());
        assertEquals(id, users.findByUsernameIgnoreCase("persistencetest").orElseThrow().getId());
        assertTrue(users.existsByUsernameIgnoreCase("PERSISTENCETEST"));
        assertTrue(users.existsByEmailIgnoreCase("PERSISTENCE@EXAMPLE.INVALID"));
        assertFalse(users.existsByUsernameIgnoreCase("missing-user"));
    }

    @Test
    void persistsVerificationAndAccountChanges() {
        User user = users.saveAndFlush(new User("AccountTest", "account@example.invalid", "original-hash"));
        Instant verifiedAt = Instant.parse("2026-11-11T12:00:00Z");
        user.setUsername("UpdatedAccountTest");
        user.setEmail("updated@example.invalid");
        user.setPasswordHash("updated-hash");
        user.setEmailVerifiedAt(verifiedAt);
        user.setEnabled(false);
        users.flush();
        entityManager.clear();

        User loaded = users.findById(user.getId()).orElseThrow();

        assertEquals("UpdatedAccountTest", loaded.getUsername());
        assertEquals("updated@example.invalid", loaded.getEmail());
        assertEquals("updated-hash", loaded.getPasswordHash());
        assertEquals(verifiedAt, loaded.getEmailVerifiedAt());
        assertFalse(loaded.isEnabled());
    }

    @Test
    void removingRoleAndDeletingUserPreservesSharedRoles() {
        User user = new User("RoleTest", "roles@example.invalid", "test-hash");
        var userRole = roles.findByName(RoleName.USER).orElseThrow();
        var adminRole = roles.findByName(RoleName.ADMIN).orElseThrow();
        user.addRole(userRole);
        user.addRole(adminRole);
        users.saveAndFlush(user);
        user.removeRole(adminRole);
        users.flush();
        entityManager.clear();

        User loaded = users.findByUsernameIgnoreCase("RoleTest").orElseThrow();
        assertEquals(1, loaded.getRoles().size());
        assertEquals(RoleName.USER, loaded.getRoles().iterator().next().getName());
        users.delete(loaded);
        users.flush();
        entityManager.clear();

        assertTrue(users.findById(user.getId()).isEmpty());
        assertTrue(roles.findByName(RoleName.USER).isPresent());
        assertTrue(roles.findByName(RoleName.ADMIN).isPresent());
    }

    @Test
    void rejectsDuplicateUsernameIgnoringCase() {
        users.saveAndFlush(new User("UniqueNameTest", "first@example.invalid", "test-hash"));

        assertThrows(DataIntegrityViolationException.class, () ->
                users.saveAndFlush(new User("UNIQUENAMETEST", "second@example.invalid", "test-hash")));
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        users.saveAndFlush(new User("FirstEmailTest", "unique@example.invalid", "test-hash"));

        assertThrows(DataIntegrityViolationException.class, () ->
                users.saveAndFlush(new User("SecondEmailTest", "UNIQUE@EXAMPLE.INVALID", "test-hash")));
    }
}
