package com.dha.document.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * Trusts the X-User-Id/X-User-Email/X-User-Role headers the API Gateway
 * sets after verifying the caller's JWT and enforcing the zero-trust gates
 * centrally — no crypto, no JWKS fetch, no DB call here.
 *
 * X-Gateway-Secret must also match app.gateway.shared-secret, so a caller
 * reaching this service directly (bypassing the Gateway) can't just set the
 * identity headers themselves — only the Gateway knows the shared secret.
 */
@Component
@Slf4j
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Value("${app.gateway.shared-secret}")
    private String gatewaySharedSecret;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");
        String role = request.getHeader("X-User-Role");
        String gatewaySecretHeader = request.getHeader("X-Gateway-Secret");

        boolean secretValid = gatewaySecretHeader != null && MessageDigest.isEqual(
                gatewaySecretHeader.getBytes(StandardCharsets.UTF_8),
                gatewaySharedSecret.getBytes(StandardCharsets.UTF_8)
        );

        if (userIdHeader != null && role != null && secretValid && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UUID userId = UUID.fromString(userIdHeader);
                JwtPrincipal principal = new JwtPrincipal(userId, email, role);
                var authToken = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (IllegalArgumentException e) {
                log.debug("Malformed X-User-Id header: {}", userIdHeader);
            }
        }

        filterChain.doFilter(request, response);
    }
}
