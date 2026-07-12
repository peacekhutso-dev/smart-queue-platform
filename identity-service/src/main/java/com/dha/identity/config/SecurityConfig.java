package com.dha.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * spring-boot-starter-security is on the classpath only for PasswordEncoder
 * and the WebAuthn RelyingParty's crypto needs — this service otherwise has
 * no Spring-Security-protected resources of its own. Without this bean,
 * Spring Boot's default autoconfiguration would put every endpoint (including
 * /register, /login, and the public JWKS endpoint) behind generated HTTP
 * Basic credentials, so explicitly permit everything here instead. The
 * self-service endpoints (verify-otp, verify-id, /me, webauthn/*) trust the
 * X-User-Email header the Gateway forwards after verifying the JWT itself,
 * not this filter chain.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
