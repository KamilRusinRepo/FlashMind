package com.example.backend.admin;

import java.time.Instant;
import java.util.stream.Collectors;

import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableConfigurationProperties(AdminProperties.class)
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AdminProperties properties;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public AdminInitializer(AdminProperties properties, UserRepository users, RoleRepository roles,
                            PasswordEncoder passwordEncoder, Validator validator) {
        this.properties = properties;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.existsByRoles_Name(RoleName.ADMIN)) {
            log.info("Administrator initialization skipped: an ADMIN account already exists.");
            return;
        }
        if (properties.isEmpty()) {
            log.info("Administrator initialization skipped: set ADMIN_USERNAME, ADMIN_EMAIL and ADMIN_PASSWORD to create an administrator.");
            return;
        }

        var violations = validator.validate(properties);
        if (!violations.isEmpty()) {
            String messages = violations.stream().map(violation -> violation.getMessage())
                    .sorted().collect(Collectors.joining(" "));
            throw new IllegalStateException("Invalid administrator configuration: " + messages);
        }
        if (users.existsByUsernameIgnoreCase(properties.username())
                || users.existsByEmailIgnoreCase(properties.email())) {
            throw new IllegalStateException("Cannot initialize administrator: ADMIN_USERNAME or ADMIN_EMAIL belongs to an existing account. No existing account was modified.");
        }

        var adminRole = roles.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("The ADMIN role is missing. Check database migrations."));
        var userRole = roles.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("The USER role is missing. Check database migrations."));

        User admin = new User(properties.username(), properties.email(), passwordEncoder.encode(properties.password()));
        admin.addRole(adminRole);
        admin.addRole(userRole);
        admin.setEmailVerifiedAt(Instant.now());
        users.saveAndFlush(admin);
        log.info("Administrator account created successfully.");
    }
}
