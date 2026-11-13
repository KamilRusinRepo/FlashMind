package com.example.backend.mail;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MailProperties.class, PasswordResetMailProperties.class})
public class MailConfiguration {
    public static final String EXCHANGE = "flashmind.mail";
    public static final String QUEUE = "flashmind.mail.verification";
    public static final String ROUTING_KEY = "verification";
    public static final String FAILED_EXCHANGE = "flashmind.mail.failed";
    public static final String FAILED_QUEUE = "flashmind.mail.verification.failed";
    public static final String RESET_QUEUE = "flashmind.mail.password-reset";
    public static final String RESET_ROUTING_KEY = "password-reset";
    public static final String RESET_FAILED_QUEUE = "flashmind.mail.password-reset.failed";

    @Bean
    Declarables mailTopology() {
        var exchange = new DirectExchange(EXCHANGE);
        var failedExchange = new DirectExchange(FAILED_EXCHANGE);
        var queue = QueueBuilder.durable(QUEUE)
                .deadLetterExchange(FAILED_EXCHANGE).deadLetterRoutingKey(ROUTING_KEY).build();
        var failed = QueueBuilder.durable(FAILED_QUEUE).build();
        var reset = QueueBuilder.durable(RESET_QUEUE)
                .deadLetterExchange(FAILED_EXCHANGE).deadLetterRoutingKey(RESET_ROUTING_KEY).build();
        var resetFailed = QueueBuilder.durable(RESET_FAILED_QUEUE).build();
        return new Declarables(exchange, failedExchange, queue, failed, reset, resetFailed,
                BindingBuilder.bind(reset).to(exchange).with(RESET_ROUTING_KEY),
                BindingBuilder.bind(resetFailed).to(failedExchange).with(RESET_ROUTING_KEY),
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(failed).to(failedExchange).with(ROUTING_KEY));
    }

    @Bean
    JacksonJsonMessageConverter mailMessageConverter() {
        return new JacksonJsonMessageConverter("com.example.backend.mail");
    }

    @Bean
    MessageRecoverer mailMessageRecoverer() {
        // Do not log the failed message or SMTP exception: they can contain the token/address.
        return (message, cause) -> {
            throw new AmqpRejectAndDontRequeueException("Email delivery exhausted its retries.");
        };
    }

    @Bean
    ContainerCustomizer<SimpleMessageListenerContainer> mailContainerCustomizer() {
        return container -> container.setErrorHandler(failure -> {
            // Spring's default handler may include the raw message in its exception log.
            org.slf4j.LoggerFactory.getLogger(MailConfiguration.class)
                    .warn("Email message rejected; inspect the failed-mail queue.");
            throw new AmqpRejectAndDontRequeueException("Email message rejected.");
        });
    }
}
