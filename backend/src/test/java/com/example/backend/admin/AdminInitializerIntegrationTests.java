package com.example.backend.admin;

import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AdminInitializerIntegrationTests {

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private Validator validator;

    @Autowired
    private EntityManager entityManager;

    private AdminInitializer initializer(AdminProperties properties) {
        return new AdminInitializer(properties, users, roles, encoder, validator);
    }

    @Test
    void createsAdminEvenWhenRegularUsersAlreadyExist() {
        var regular = new User("ExistingRegularUser", "regular-admin-test@example.invalid", "test-hash");
        regular.addRole(roles.findByName(RoleName.USER).orElseThrow());
        users.saveAndFlush(regular);
        long count = users.count();

        initializer(new AdminProperties("BootstrapAdmin", "bootstrap@example.invalid", "ExamplePassword123!")).run();
        entityManager.clear();

        User admin = users.findByEmailIgnoreCase("bootstrap@example.invalid").orElseThrow();
        assertEquals(count + 1, users.count());
        assertTrue(users.existsByRoles_Name(RoleName.ADMIN));
        assertEquals(2, admin.getRoles().size());
        assertNotNull(admin.getEmailVerifiedAt());
        assertTrue(encoder.matches("ExamplePassword123!", admin.getPasswordHash()));
        assertEquals(1, users.findById(regular.getId()).orElseThrow().getRoles().size());
    }

    @Test
    void repeatedInitializationNeverOverwritesAdministratorPassword() {
        var properties = new AdminProperties("IdempotentAdmin", "idempotent@example.invalid", "OriginalPassword123!");
        initializer(properties).run();
        User admin = users.findByEmailIgnoreCase(properties.email()).orElseThrow();
        String updatedHash = encoder.encode("UpdatedPassword123!");
        admin.setPasswordHash(updatedHash);
        users.flush();
        long count = users.count();

        initializer(new AdminProperties("AnotherAdmin", "another@example.invalid", "DifferentPassword123!")).run();
        initializer(new AdminProperties(null, null, null)).run();
        entityManager.clear();

        assertEquals(count, users.count());
        User loaded = users.findById(admin.getId()).orElseThrow();
        assertEquals(updatedHash, loaded.getPasswordHash());
        assertTrue(encoder.matches("UpdatedPassword123!", loaded.getPasswordHash()));
    }
}
