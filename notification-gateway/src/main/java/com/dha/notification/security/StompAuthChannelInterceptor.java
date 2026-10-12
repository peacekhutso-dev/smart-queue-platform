package com.dha.notification.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

/**
 * Previously this endpoint had no authentication at all: anyone could open
 * /ws/notifications and subscribe to /topic/user/{anyUserId}/payment-status
 * to read another user's payment amount/status. Identity is established
 * during the WebSocket handshake (see HandshakeAuthInterceptor/
 * PrincipalHandshakeHandler, reading the httpOnly dha_token cookie) and just
 * checked here; this also enforces that a SUBSCRIBE to a user-scoped topic
 * matches the connecting principal's own userId.
 */
@Component
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            if (!(accessor.getUser() instanceof StompPrincipal)) {
                throw new BadCredentialsException("Missing or invalid session cookie");
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal user = accessor.getUser();
            if (!(user instanceof StompPrincipal principal)) {
                throw new BadCredentialsException("Not authenticated");
            }
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/user/")) {
                String targetUserId = destination.substring("/topic/user/".length()).split("/")[0];
                boolean isStaff = "STAFF".equals(principal.role()) || "ADMIN".equals(principal.role());
                if (!isStaff && !principal.userId().toString().equals(targetUserId)) {
                    throw new AccessDeniedException("Cannot subscribe to another user's notification topic");
                }
            }
        }

        return message;
    }

    public record StompPrincipal(UUID userId, String role) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }
}
