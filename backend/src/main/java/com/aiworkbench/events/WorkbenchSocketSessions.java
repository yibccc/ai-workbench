package com.aiworkbench.events;

import com.aiworkbench.security.WorkbenchPrincipal;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.ApplicationListener;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

/** Tracks transport sessions because Spring's user registry alone does not hold the HTTP session id. */
@Component
public class WorkbenchSocketSessions implements WebSocketHandlerDecoratorFactory,
        ApplicationListener<SessionDestroyedEvent> {
    private final ConcurrentMap<String, SocketIdentity> sockets = new ConcurrentHashMap<>();

    public record SocketIdentity(WebSocketSession socket, String httpSessionId, WorkbenchPrincipal principal) {
        public String stompSessionId() { return socket.getId(); }
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                Object sessionId = session.getAttributes().get(WorkbenchWebSocketConfig.HTTP_SESSION_ID);
                Object principal = session.getAttributes().get(WorkbenchWebSocketConfig.PRINCIPAL);
                if (!(sessionId instanceof String id) || !(principal instanceof WorkbenchPrincipal user)) {
                    session.close(CloseStatus.POLICY_VIOLATION);
                    return;
                }
                sockets.put(session.getId(), new SocketIdentity(session, id, user));
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception exception) {
                    sockets.remove(session.getId());
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sockets.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    public SocketIdentity find(String stompSessionId) {
        return sockets.get(stompSessionId);
    }

    public List<SocketIdentity> forUser(UUID userId) {
        return sockets.values().stream().filter(socket -> socket.principal().userId().equals(userId)).toList();
    }

    public void closeSession(String httpSessionId) {
        sockets.values().stream().filter(socket -> socket.httpSessionId().equals(httpSessionId))
                .forEach(this::close);
    }

    public void close(SocketIdentity identity) {
        sockets.remove(identity.stompSessionId(), identity);
        try {
            if (identity.socket().isOpen()) identity.socket().close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException | RuntimeException ignored) {
            // The session is no longer eligible for event delivery even if transport cleanup fails.
        }
    }

    @Override
    public void onApplicationEvent(SessionDestroyedEvent event) {
        closeSession(event.getSessionId());
    }
}
