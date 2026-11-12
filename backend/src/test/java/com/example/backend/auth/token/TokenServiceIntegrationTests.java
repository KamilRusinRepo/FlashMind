package com.example.backend.auth.token;

import com.example.backend.support.PostgresIntegrationTest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TokenServiceIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    private TokenService service;

    @Autowired
    private UserTokenRepository tokens;

    @Autowired
    private UserRepository users;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void persistsHashPurposeAndOwnerThenConsumesOnlyOnce() throws Exception {
        User user = createUser();
        var issued = service.issue(user.getId(), TokenType.EMAIL_VERIFICATION);
        UserToken stored = tokens.findByTokenHash(hash(issued.value())).orElseThrow();

        assertEquals(user.getId(), stored.getUser().getId());
        assertEquals(TokenType.EMAIL_VERIFICATION, stored.getType());
        assertNull(stored.getUsedAt());
        assertEquals(64, stored.getTokenHash().length());
        assertTrue(stored.getExpiresAt().isAfter(stored.getCreatedAt()));
        assertEquals(user.getId(), service.consume(issued.value(), TokenType.EMAIL_VERIFICATION).getId());
        assertNotNull(tokens.findById(stored.getId()).orElseThrow().getUsedAt());
        assertThrows(InvalidTokenException.class, () -> service.consume(issued.value(), TokenType.EMAIL_VERIFICATION));
    }

    @Test
    void wrongTypeLeavesTokenAvailableForCorrectPurpose() {
        var issued = service.issue(createUser().getId(), TokenType.PASSWORD_RESET);

        assertThrows(InvalidTokenException.class, () -> service.consume(issued.value(), TokenType.EMAIL_VERIFICATION));

        assertNotNull(service.consume(issued.value(), TokenType.PASSWORD_RESET).getId());
    }

    @Test
    void expiredDatabaseTokenIsRejected() throws Exception {
        User user = createUser();
        String rawToken = "B".repeat(43);
        Instant now = Instant.now();
        UserToken expired = tokens.saveAndFlush(new UserToken(user, hash(rawToken), TokenType.EMAIL_VERIFICATION,
                now.minusSeconds(120), now.minusSeconds(60)));

        assertThrows(InvalidTokenException.class, () -> service.consume(rawToken, TokenType.EMAIL_VERIFICATION));
        assertNull(tokens.findById(expired.getId()).orElseThrow().getUsedAt());
    }

    @Test
    void concurrentConsumptionHasExactlyOneSuccessfulCaller() throws Exception {
        var issued = service.issue(createUser().getId(), TokenType.EMAIL_VERIFICATION);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            try {
                service.consume(issued.value(), TokenType.EMAIL_VERIFICATION);
                return true;
            } catch (InvalidTokenException exception) {
                return false;
            }
        };
        try {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedAccountChangeRollsBackTokenConsumption() throws Exception {
        User owner = createUser();
        var issued = service.issue(owner.getId(), TokenType.EMAIL_VERIFICATION);
        var transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            User user = service.consume(issued.value(), TokenType.EMAIL_VERIFICATION);
            user.setEmailVerifiedAt(Instant.now());
            throw new IllegalStateException("Simulated account update failure");
        }));

        assertNull(tokens.findByTokenHash(hash(issued.value())).orElseThrow().getUsedAt());
        assertNull(users.findById(owner.getId()).orElseThrow().getEmailVerifiedAt());
        assertEquals(owner.getId(), service.consume(issued.value(), TokenType.EMAIL_VERIFICATION).getId());
    }

    @Test
    void deletingOwnerRemovesTokensWithoutDeletingOtherUsersTokens() {
        User deleted = createUser();
        User retained = createUser();
        service.issue(deleted.getId(), TokenType.EMAIL_VERIFICATION);
        var retainedToken = service.issue(retained.getId(), TokenType.PASSWORD_RESET);
        long count = tokens.count();

        users.deleteById(deleted.getId());

        assertEquals(count - 1, tokens.count());
        assertEquals(retained.getId(), service.consume(retainedToken.value(), TokenType.PASSWORD_RESET).getId());
    }

    private User createUser() {
        String name = "token_" + UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(new User(name, name + "@example.invalid", "test-hash"));
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
