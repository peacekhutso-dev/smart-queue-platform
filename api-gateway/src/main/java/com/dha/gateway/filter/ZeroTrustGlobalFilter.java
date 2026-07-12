package com.dha.gateway.filter;

import com.dha.gateway.security.JwtVerifier;
import com.dha.gateway.security.VerifiedIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Centralizes what used to be split across the monolith's JwtAuthFilter +
 * ZeroTrustFilter, run once here instead of independently in every
 * downstream service. Three path tiers:
 *
 *   NO_AUTH     — no JWT required at all (register/login/webhooks/health/ws handshake).
 *   GATE1_ONLY  — a valid JWT is required, but 2FA/face/fingerprint gates are
 *                 skipped (these are exactly the endpoints that *grant* those
 *                 gates — verify-otp, verify-id, /me, webauthn/*, face/*).
 *   FULL        — valid JWT + 2FA gate + face-or-fingerprint gate, depending on role.
 *
 * On success, forwards trusted identity as X-User-Id/X-User-Email/X-User-Role
 * headers — any client-supplied versions of those headers are stripped first
 * so a request can't spoof identity by just setting them directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZeroTrustGlobalFilter implements GlobalFilter, Ordered {

    private final JwtVerifier jwtVerifier;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.zero-trust.dev-bypass:false}")
    private boolean devBypass;

    private static final String TWO_FA_PREFIX = "2fa:";
    private static final String FACE_PREFIX = "face:";
    private static final String FINGERPRINT_PREFIX = "fingerprint:";

    private static final List<String> NO_AUTH_PREFIXES = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/send-otp",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/payments/webhooks",
            "/ws",
            "/actuator",
            "/error"
    );

    private static final List<String> GATE1_ONLY_PREFIXES = List.of(
            "/api/auth/verify-otp",
            "/api/auth/verify-id",
            "/api/auth/me",
            "/api/auth/webauthn",
            "/api/auth/face"
    );

    @Override
    public int getOrder() {
        return -100; // run before routing
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // No token expected at all on these paths (register/login/webhooks/...) —
        // nothing to verify, nothing to forward.
        if (matches(path, NO_AUTH_PREFIXES)) {
            return chain.filter(stripSpoofableHeaders(exchange));
        }

        // Gate 1 (a valid JWT identifying the caller) always applies, even under
        // dev-bypass — dev-bypass only skips the 2FA/face/fingerprint Redis
        // checks below. Without this, downstream services never learn who's
        // calling and reject everything themselves.
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Authentication required");
        }

        String token = authHeader.substring(7);
        boolean skipGates2And3 = devBypass || matches(path, GATE1_ONLY_PREFIXES);

        return jwtVerifier.verify(token)
                .flatMap(identity -> skipGates2And3
                        ? forward(exchange, chain, identity)
                        : enforceZeroTrustGates(exchange, chain, identity))
                .onErrorResume(e -> {
                    log.debug("JWT verification failed: {}", e.getMessage());
                    return reject(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid or expired token");
                });
    }

    private Mono<Void> enforceZeroTrustGates(ServerWebExchange exchange, GatewayFilterChain chain, VerifiedIdentity identity) {
        String userId = identity.userId().toString();
        boolean isStaff = "STAFF".equals(identity.role()) || "ADMIN".equals(identity.role());
        String gate3Key = (isStaff ? FINGERPRINT_PREFIX : FACE_PREFIX) + userId;
        String gate3Code = isStaff ? "REQUIRES_FINGERPRINT" : "REQUIRES_FACE";
        String gate3Msg = isStaff ? "Fingerprint verification required" : "Face verification required";

        return redisTemplate.hasKey(TWO_FA_PREFIX + userId)
                .flatMap(has2fa -> {
                    if (!Boolean.TRUE.equals(has2fa)) {
                        return reject(exchange, HttpStatus.FORBIDDEN, "REQUIRES_2FA", "Two-factor authentication required");
                    }
                    return redisTemplate.hasKey(gate3Key).flatMap(hasGate3 -> {
                        if (!Boolean.TRUE.equals(hasGate3)) {
                            return reject(exchange, HttpStatus.FORBIDDEN, gate3Code, gate3Msg);
                        }
                        return forward(exchange, chain, identity);
                    });
                });
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, VerifiedIdentity identity) {
        ServerHttpRequest mutated = stripSpoofableHeaders(exchange).getRequest().mutate()
                .header("X-User-Id", identity.userId().toString())
                .header("X-User-Email", identity.email())
                .header("X-User-Role", identity.role())
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private ServerWebExchange stripSpoofableHeaders(ServerWebExchange exchange) {
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Email");
                    h.remove("X-User-Role");
                })
                .build();
        return exchange.mutate().request(stripped).build();
    }

    private boolean matches(String path, List<String> prefixes) {
        return prefixes.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of("success", false, "code", code, "message", message));
        } catch (Exception e) {
            body = "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
