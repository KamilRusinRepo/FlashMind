package com.example.backend.support;

import java.util.UUID;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Shared, disposable database for Spring integration tests, independent of Compose and .env. */
@Sql(statements = {"DELETE FROM decks", "DELETE FROM users"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class PostgresIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("flashmind_test")
            .withUsername("flashmind_test")
            .withPassword(UUID.randomUUID().toString());

    static {
        // Keep it alive across cached Spring contexts; Testcontainers/Ryuk cleans up at JVM exit.
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        // Cached Spring contexts each own a pool but share this PostgreSQL instance.
        // Two connections also allow the existing concurrent-request tests to run.
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> 2);
        properties.add("spring.datasource.hikari.minimum-idle", () -> 0);
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.sql.init.mode", () -> "never");
        properties.add("spring.rabbitmq.listener.simple.auto-startup", () -> false);
        properties.add("spring.rabbitmq.dynamic", () -> false);
        properties.add("app.reminders.enabled", () -> false); // Tests invoke scheduling explicitly; no background jobs.
        // Never bootstrap a real administrator from the developer's environment in tests.
        properties.add("app.admin.username", () -> "");
        properties.add("app.admin.email", () -> "");
        properties.add("app.admin.password", () -> "");
    }
}
