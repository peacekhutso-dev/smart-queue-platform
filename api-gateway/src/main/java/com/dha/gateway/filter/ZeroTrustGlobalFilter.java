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
import org.springframework.http.HttpCookie;
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

    @Value("${app.gateway.shared-secret}")
    private String gatewaySharedSecret;

    private static final String TWO_FA_PREFIX = "2fa:";
    private static final String IDENTITY_PREFIX = "identity-confirmed:";
    private static final String FACE_PREFIX = "face:";
    private static final String FINGERPRINT_PREFIX = "fingerprint:";

    private static final List<String> NO_AUTH_PREFIXES = List.of(
            "/api/auth/check-availability",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/send-otp",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/logout",
            "/api/payments/webhooks",
            "/ws",
            "/actuator",
            "/error"
    );

    private static final List<String> GATE1_ONLY_PREFIXES = List.of(
            "/api/auth/verify-otp",
            "/api/auth/verify-id",
            "/api/auth/confirm-identity",
            "/api/auth/me",
            "/api/auth/webauthn",
            "/api/auth/face"
    );

    private static final String TOKEN_COOKIE_NAME = "dha_token";

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
        String token = extractToken(exchange);
        if (token == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Authentication required");
        }

        boolean skipGates2And3 = devBypass || matches(path, GATE1_ONLY_PREFIXES);

        return jwtVerifier.verify(token)
                .flatMap(identity -> skipGates2And3
                        ? forward(exchange, chain, identity)
                        : enforceZeroTrustGates(exchange, chain, identity))
                .onErrorResume(e -> {
                    log.warn("JWT verification failed path={} reason={}", path, e.getMessage());
                    return reject(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid or expired token");
                });
    }

    // The frontend now carries its session in an httpOnly dha_token cookie
    // (not readable/settable by JS, so not stealable via XSS the way a
    // localStorage token was) — checked first. Authorization: Bearer is kept
    // as a fallback for non-browser callers (mobile clients, API testing).
    private String extractToken(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(TOKEN_COOKIE_NAME);
        if (cookie != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
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
                    // Gate 1.5: login must have confirmed the citizen's SA ID
                    // + name against Home Affairs' own (seeded) record — see
                    // identity-service's AuthService.confirmIdentity. Staff/admin
                    // aren't citizens in this system's population-register
                    // concept, so this gate doesn't apply to them (same
                    // isStaff branch already used for gate 3 below).
                    if (isStaff) {
                        return enforceGate3(exchange, chain, identity, gate3Key, gate3Code, gate3Msg);
                    }
                    return redisTemplate.hasKey(IDENTITY_PREFIX + userId).flatMap(hasIdentity -> {
                        if (!Boolean.TRUE.equals(hasIdentity)) {
                            return reject(exchange, HttpStatus.FORBIDDEN, "REQUIRES_IDENTITY_CONFIRMATION",
                                    "Please confirm your identity against your Home Affairs record");
                        }
                        return enforceGate3(exchange, chain, identity, gate3Key, gate3Code, gate3Msg);
                    });
                });
    }

    private Mono<Void> enforceGate3(ServerWebExchange exchange, GatewayFilterChain chain, VerifiedIdentity identity,
                                     String gate3Key, String gate3Code, String gate3Msg) {
        return redisTemplate.hasKey(gate3Key).flatMap(hasGate3 -> {
            if (!Boolean.TRUE.equals(hasGate3)) {
                return reject(exchange, HttpStatus.FORBIDDEN, gate3Code, gate3Msg);
            }
            return forward(exchange, chain, identity);
        });
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, VerifiedIdentity identity) {
        log.info("Gate PASSED userId={} role={} method={} path={}",
                identity.userId(), identity.role(),
                exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath());
        ServerHttpRequest mutated = stripSpoofableHeaders(exchange).getRequest().mutate()
                .header("X-User-Id", identity.userId().toString())
                .header("X-User-Email", identity.email())
                .header("X-User-Role", identity.role())
                .header("X-Gateway-Secret", gatewaySharedSecret)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    // Strips any client-supplied identity/secret headers so a caller can't
    // spoof the Gateway's own trust signal to a downstream service, even if
    // that service is reachable directly (see HeaderAuthFilter in each
    // downstream service, which rejects requests missing X-Gateway-Secret).
    private ServerWebExchange stripSpoofableHeaders(ServerWebExchange exchange) {
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Email");
                    h.remove("X-User-Role");
                    h.remove("X-Gateway-Secret");
                })
                .build();
        return exchange.mutate().request(stripped).build();
    }

    private boolean matches(String path, List<String> prefixes) {
        return prefixes.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        log.warn("Gate REJECTED method={} path={} status={} code={} reason=\"{}\"",
                exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(),
                status.value(), code, message);
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
