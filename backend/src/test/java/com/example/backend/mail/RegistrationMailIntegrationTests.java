package com.example.backend.mail;

import com.example.backend.support.PostgresIntegrationTest;

import java.util.UUID;

import com.example.backend.auth.RegistrationService;
import com.example.backend.auth.dto.request.RegisterRequest;
import com.example.backend.auth.token.TokenService;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"})
class RegistrationMailIntegrationTests extends PostgresIntegrationTest {
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired UserTokenRepository tokens;
    @Autowired TokenService tokenService;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean RabbitTemplate rabbit;

    @BeforeEach
    void confirmPublications() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void publishesOnlyAfterCommitAndTokenBelongsToNewAccount() {
        String username = username();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            registration.register(request(username));
            verifyNoInteractions(rabbit);
        });
        var captured = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(MailConfiguration.EXCHANGE), eq(MailConfiguration.ROUTING_KEY),
                captured.capture(), any(MessagePostProcessor.class), any(CorrelationData.class));
        var event = (VerificationEmailRequested) captured.getValue();
        var user = users.findByUsernameIgnoreCase(username).orElseThrow();
        assertEquals(user.getId(), event.userId());
        assertEquals(user.getEmail(), event.recipient());
        assertEquals(user.getId(), tokenService.consume(event.token(), TokenType.EMAIL_VERIFICATION).getId());
        assertNull(user.getEmailVerifiedAt());
    }

    @Test
    void rollbackRemovesAccountAndTokenWithoutPublishing() {
        String username = username();
        long tokenCount = tokens.count();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            registration.register(request(username));
            status.setRollbackOnly();
        });
        assertFalse(users.existsByUsernameIgnoreCase(username));
        assertEquals(tokenCount, tokens.count());
        verifyNoInteractions(rabbit);
    }

    @Test
    void brokerFailureDoesNotUndoCommittedAccountOrReportRegistrationFailure() {
        doThrow(new IllegalStateException("Broker unavailable")).when(rabbit).convertAndSend(
                anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        String username = username();
        var response = assertDoesNotThrow(() -> registration.register(request(username)));
        assertTrue(users.existsByUsernameIgnoreCase(username));
        assertNotNull(response.id());
    }

    private String username() { return "mail_" + UUID.randomUUID().toString().replace("-", ""); }
    private RegisterRequest request(String username) {
        return new RegisterRequest(username, username + "@example.com", "ExamplePassword123!");
    }
}
