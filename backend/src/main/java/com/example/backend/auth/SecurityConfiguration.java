package com.example.backend.auth;

import com.example.backend.error.SecurityErrorHandler;
import com.example.backend.user.UserRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import java.util.List;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfiguration {
    @Bean
    AuthenticationManager authenticationManager(LoginAuthenticationProvider provider) {
        return new ProviderManager(List.of(provider));
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    SessionAuthenticationStrategy loginSessionStrategy(HttpSessionCsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errors,
                                           AuthenticationManager authenticationManager,
                                           HttpSessionCsrfTokenRepository csrfTokens,
                                           SecurityContextRepository contexts, UserRepository users) throws Exception {
        return http
                .authenticationManager(authenticationManager)
                .securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/status", "/api/auth/csrf", "/api/auth/login", "/api/auth/register",
                                "/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/forgot-password",
                                "/api/auth/reset-password").permitAll()
                        .anyRequest().authenticated())
                // Public access does not exempt a POST from CSRF protection.
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .addFilterBefore(new SessionVersionFilter(users), CsrfFilter.class)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .permitAll())
                .build();
    }
}
