package com.dha.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * This service's SecurityConfig deliberately permits every request (see its
 * javadoc) because /register, /login etc. need no identity at all. But
 * /verify-otp, /verify-id, /me, and every WebAuthnController endpoint trust
 * the X-User-Email header the Gateway forwards after verifying the caller's
 * JWT — with nothing else checking that the header actually came from the
 * Gateway. Reaching this service directly (its docker-compose port is
 * published to the host) let anyone set X-User-Email to an arbitrary victim
 * and, for example, register a WebAuthn credential bound to that email —
 * full account takeover. This filter rejects any request carrying
 * X-User-Email unless X-Gateway-Secret matches app.gateway.shared-secret.
 * The same applies to X-User-Role / X-User-Id (the admin citizen-records
 * endpoints trust X-User-Role), and to every /internal/** request
 * (service-to-service only), whether or not it carries identity headers.
 */
@Component
public class GatewaySecretFilter extends OncePerRequestFilter {

    @Value("${app.gateway.shared-secret}")
    private String gatewaySharedSecret;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        boolean carriesIdentity = request.getHeader("X-User-Email") != null
                || request.getHeader("X-User-Role") != null
                || request.getHeader("X-User-Id") != null;
        boolean internalPath = request.getRequestURI().startsWith("/internal/");
        if (carriesIdentity || internalPath) {
            String gatewaySecretHeader = request.getHeader("X-Gateway-Secret");
            boolean secretValid = gatewaySecretHeader != null && MessageDigest.isEqual(
                    gatewaySecretHeader.getBytes(StandardCharsets.UTF_8),
                    gatewaySharedSecret.getBytes(StandardCharsets.UTF_8)
            );
            if (!secretValid) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"success\":false,\"message\":\"Forbidden\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
