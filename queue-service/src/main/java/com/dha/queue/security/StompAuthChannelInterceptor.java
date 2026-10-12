package com.dha.queue.security;

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
 * The Gateway exempts /ws from JWT verification entirely (it can't see
 * inside the STOMP protocol, and the JWT now lives in an httpOnly cookie
 * anyway, invisible to the JS client that used to attach it as a STOMP
 * CONNECT header) — so identity is established during the WebSocket
 * handshake instead (see HandshakeAuthInterceptor/PrincipalHandshakeHandler)
 * and just checked here. Previously, before any of that existed, any client
 * could open the socket and subscribe to any /topic/user/{userId}/status or
 * /topic/branch/{branchId}/queue with no authentication at all.
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
                    throw new AccessDeniedException("Cannot subscribe to another user's status topic");
                }
            }
            // /topic/branch/{id}/queue is intentionally open to any authenticated
            // user — every citizen waiting at that branch legitimately needs it.
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
