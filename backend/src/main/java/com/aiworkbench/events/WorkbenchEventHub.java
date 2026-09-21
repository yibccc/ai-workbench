package com.aiworkbench.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class WorkbenchEventHub extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicLong revisions = new AtomicLong();
    private final ObjectMapper objectMapper;

    public WorkbenchEventHub(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override public void afterConnectionEstablished(WebSocketSession session) { sessions.add(session); }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session); }

    public void publishAfterCommit(String kind, UUID entityId, String state) {
        Runnable publish = () -> publish(new WorkbenchEvent(UUID.randomUUID(), kind, entityId, state,
                revisions.incrementAndGet(), Instant.now()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else publish.run();
    }

    private void publish(WorkbenchEvent event) {
        try {
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(event));
            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) { sessions.remove(session); continue; }
                try { synchronized (session) { session.sendMessage(message); } }
                catch (Exception ignored) { sessions.remove(session); }
            }
        } catch (Exception ignored) {
            // Notification is best-effort; the committed database state remains authoritative.
        }
    }
}
