package com.aiworkbench.events;

import com.aiworkbench.security.SessionAccess;
import com.aiworkbench.security.WorkbenchPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Map;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Frame authorization runs before the simple broker sees client destinations. */
@Component
public class WorkbenchStompGuard implements ChannelInterceptor {
    private final SessionAccess access;
    private final WorkbenchSocketSessions sockets;

    public WorkbenchStompGuard(SessionAccess access, WorkbenchSocketSessions sockets) {
        this.access = access;
        this.sockets = sockets;
    }

    SessionAccess access() { return access; }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor frame = StompHeaderAccessor.wrap(message);
        StompCommand command = frame.getCommand();
        if (command == null) return message;
        if (command == StompCommand.DISCONNECT) return message;
        Identity identity = requireIdentity(frame);
        try {
            if (!access.isLive(identity.principal(), identity.httpSessionId())) {
                deny(frame, "Session is no longer active");
            }
        } catch (RuntimeException exception) {
            close(frame.getSessionId());
            throw new AccessDeniedException("Session is no longer active", exception);
        }
        if (command == StompCommand.CONNECT) {
            String supplied = frame.getFirstNativeHeader("X-XSRF-TOKEN");
            if (!equalToken(identity.csrfToken(), supplied)) deny(frame, "Invalid STOMP CSRF token");
        } else if (command == StompCommand.SUBSCRIBE) {
            if (!WorkbenchWebSocketConfig.USER_DESTINATION.equals(frame.getDestination())) {
                deny(frame, "Subscription destination is not allowed");
            }
        } else if (command != StompCommand.UNSUBSCRIBE) {
            deny(frame, "Client command is not allowed");
        }
        return message;
    }

    ChannelInterceptor outbound() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor frame = StompHeaderAccessor.wrap(message);
                if (frame.getMessageType() != SimpMessageType.MESSAGE) return message;
                WorkbenchSocketSessions.SocketIdentity socket = sockets.find(frame.getSessionId());
                if (socket == null) return null;
                try {
                    if (access.isLive(socket.principal(), socket.httpSessionId())) return message;
                } catch (RuntimeException ignored) {
                    // Redis or database uncertainty must not release the message.
                }
                sockets.close(socket);
                return null;
            }
        };
    }

    private Identity requireIdentity(StompHeaderAccessor frame) {
        Map<String, Object> attributes = frame.getSessionAttributes();
        Object principal = attributes == null ? null : attributes.get(WorkbenchWebSocketConfig.PRINCIPAL);
        Object sessionId = attributes == null ? null : attributes.get(WorkbenchWebSocketConfig.HTTP_SESSION_ID);
        Object token = attributes == null ? null : attributes.get(WorkbenchWebSocketConfig.CSRF_TOKEN);
        Principal actual = frame.getUser();
        if (!(principal instanceof WorkbenchPrincipal user) || !(sessionId instanceof String id)
                || !(token instanceof String csrf) || !(actual instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof WorkbenchPrincipal authenticated)
                || !user.equals(authenticated)) {
            deny(frame, "Authentication is required");
        }
        return new Identity((WorkbenchPrincipal) principal, (String) sessionId, (String) token);
    }

    private void deny(StompHeaderAccessor frame, String reason) {
        close(frame.getSessionId());
        throw new AccessDeniedException(reason);
    }

    private void close(String stompSessionId) {
        WorkbenchSocketSessions.SocketIdentity socket = sockets.find(stompSessionId);
        if (socket != null) sockets.close(socket);
    }

    private static boolean equalToken(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private record Identity(WorkbenchPrincipal principal, String httpSessionId, String csrfToken) { }
}
