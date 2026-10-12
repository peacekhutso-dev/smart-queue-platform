package com.dha.notification.security;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Reads the Principal HandshakeAuthInterceptor attached to the handshake
 * attributes and attaches it to the WebSocketSession, so it's available on
 * every subsequent STOMP frame's accessor.getUser() for this connection.
 */
@Component
public class PrincipalHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(@NonNull org.springframework.http.server.ServerHttpRequest request,
                                       @NonNull org.springframework.web.socket.WebSocketHandler wsHandler,
                                       @NonNull Map<String, Object> attributes) {
        Object principal = attributes.get(HandshakeAuthInterceptor.PRINCIPAL_ATTR);
        return principal instanceof Principal p ? p : null;
    }
}
