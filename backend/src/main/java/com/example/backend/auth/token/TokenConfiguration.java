package com.example.backend.auth.token;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TokenProperties.class)
public class TokenConfiguration {

    @Bean
    public Clock tokenClock() {
        return Clock.systemUTC();
    }
}
