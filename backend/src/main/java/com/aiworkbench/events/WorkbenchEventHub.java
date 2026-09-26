package com.aiworkbench.events;

import com.aiworkbench.security.SessionAccess;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Database changes remain authoritative; events only ask each owner's browser to GET again. */
@Component
public class WorkbenchEventHub {
    private final AtomicLong revisions = new AtomicLong();
    private final SimpMessagingTemplate messaging;
    private final WorkbenchSocketSessions sockets;
    private final SessionAccess access;
    private final SimpUserRegistry users;

    public WorkbenchEventHub(SimpMessagingTemplate messaging, WorkbenchSocketSessions sockets,
                             SessionAccess access, SimpUserRegistry users) {
        this.messaging = messaging;
        this.sockets = sockets;
        this.access = access;
        this.users = users;
    }

    public void publishAfterCommit(UUID ownerUserId, String kind, UUID entityId, String state) {
        if (ownerUserId == null) throw new IllegalArgumentException("Event owner is required");
        Runnable publish = () -> publish(ownerUserId, new WorkbenchEvent(UUID.randomUUID(), kind, entityId, state,
                revisions.incrementAndGet(), Instant.now()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else publish.run();
    }

    private void publish(UUID ownerUserId, WorkbenchEvent event) {
        for (WorkbenchSocketSessions.SocketIdentity socket : sockets.forUser(ownerUserId)) {
            try {
                if (!socket.socket().isOpen() || !access.isLive(socket.principal(), socket.httpSessionId())) {
                    sockets.close(socket);
                    continue;
                }
                SimpUser user = users.getUser(ownerUserId.toString());
                if (user == null || user.getSession(socket.stompSessionId()) == null) continue;
                SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                headers.setSessionId(socket.stompSessionId());
                headers.setLeaveMutable(true);
                messaging.convertAndSendToUser(ownerUserId.toString(), "/queue/workbench-events",
                        event, headers.getMessageHeaders());
            } catch (Exception ignored) {
                // A notification failure must not turn a committed database write into an HTTP failure.
                sockets.close(socket);
            }
        }
    }
}
