package com.dha.notification.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * The frontend's JWT lives in an httpOnly dha_token cookie (see
 * identity-service's AuthController), which the browser attaches
 * automatically to the SockJS/WS handshake HTTP request. Identity is
 * established here, during the handshake, rather than from a STOMP CONNECT
 * header the JS client can no longer read the token to set.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HandshakeAuthInterceptor implements HandshakeInterceptor {

    private static final String TOKEN_COOKIE_NAME = "dha_token";
    public static final String PRINCIPAL_ATTR = "principal";

    private final JwksKeyProvider jwksKeyProvider;

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                    @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return true;
        }
        String token = extractCookie(servletRequest.getServletRequest());
        if (token == null) {
            return true; // rejected later, at STOMP CONNECT, with a proper STOMP ERROR frame
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwksKeyProvider.getPublicKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            UUID userId = UUID.fromString(claims.get("userId", String.class));
            String role = claims.get("role", String.class);
            attributes.put(PRINCIPAL_ATTR, new StompAuthChannelInterceptor.StompPrincipal(userId, role));
        } catch (Exception e) {
            log.debug("WebSocket handshake: invalid or expired token: {}", e.getMessage());
        }
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                @NonNull WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (TOKEN_COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
