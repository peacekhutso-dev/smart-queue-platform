package com.dha.queue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Zero Trust Filter — every protected API call must pass three gates:
 *   1. Valid JWT (set by JwtAuthFilter upstream)
 *   2. 2FA session present in Redis  → key: 2fa:{userId}
 *   3. Face verification session     → key: face:{userId}
 *
 * Exempt paths (auth, WebSocket, actuator) bypass all checks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZeroTrustFilter extends OncePerRequestFilter {

    @Value("${app.zero-trust.dev-bypass:false}")
    private boolean devBypass;

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public static final String TWO_FA_PREFIX  = "2fa:";
    public static final String FACE_PREFIX    = "face:";

    private static final List<String> EXEMPT_PREFIXES = List.of(
            "/api/auth/",
            "/ws",
            "/actuator",
            "/error"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (devBypass) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        if (isExempt(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Gate 1 — JWT must be validated (JwtAuthFilter sets SecurityContext)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN", "Authentication required");
            return;
        }

        Long userId = extractUserIdFromToken(request.getHeader("Authorization"));
        if (userId == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN", "Cannot determine user identity");
            return;
        }

        // Gate 2 — 2FA session
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(TWO_FA_PREFIX + userId))) {
            log.debug("Zero-trust gate 2 failed for userId={}", userId);
            reject(response, HttpServletResponse.SC_FORBIDDEN, "REQUIRES_2FA", "Two-factor authentication required");
            return;
        }

        // Gate 3 — Face verification session
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(FACE_PREFIX + userId))) {
            log.debug("Zero-trust gate 3 failed for userId={}", userId);
            reject(response, HttpServletResponse.SC_FORBIDDEN, "REQUIRES_FACE", "Face verification required");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExempt(String path) {
        return EXEMPT_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Long extractUserIdFromToken(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            String token = authHeader.substring(7);
            Object raw = jwtUtil.extractClaim(token, claims -> claims.get("userId"));
            if (raw instanceof Integer i)  return i.longValue();
            if (raw instanceof Long    l)  return l;
            if (raw instanceof Number  n)  return n.longValue();
            return null;
        } catch (Exception e) {
            log.debug("JWT userId extraction failed: {}", e.getMessage());
            return null;
        }
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "success", false,
                "code",    code,
                "message", message
        )));
    }
}
