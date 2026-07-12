package com.dha.queue.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Replaces the old JwtAuthFilter now that JWT verification and the
 * zero-trust gates (2FA/face/fingerprint) are enforced once, centrally, by
 * the API Gateway's ZeroTrustGlobalFilter. This service just trusts the
 * X-User-Id/X-User-Email/X-User-Role headers the Gateway sets after
 * verifying the caller — no crypto, no JWKS fetch, no DB call.
 *
 * Security note: this only holds if the Gateway is the *only* reachable
 * ingress. The docker-compose "direct debug" port on this service (8081)
 * bypasses the Gateway entirely and, on that path, these headers are
 * whatever the caller sends — fine for local dev, must not be exposed like
 * that in a real deployment.
 */
@Component
@Slf4j
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");
        String role = request.getHeader("X-User-Role");

        if (userIdHeader != null && role != null && SecurityContextHolder.getContext().getAuthentication() == null) {
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
