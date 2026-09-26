package com.aiworkbench.events;

import com.aiworkbench.security.SessionAccess;
import com.aiworkbench.security.WorkbenchPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkbenchEventHubTest {
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final WorkbenchSocketSessions sockets = mock(WorkbenchSocketSessions.class);
    private final SessionAccess access = mock(SessionAccess.class);
    private final SimpUserRegistry users = mock(SimpUserRegistry.class);
    private final WorkbenchEventHub hub = new WorkbenchEventHub(messaging, sockets, access, users);

    @Test void sendsCommittedMetadataOnlyToOwnerSession() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        WorkbenchSocketSessions.SocketIdentity socket = socket(owner, "http-a", "stomp-a");
        when(sockets.forUser(owner)).thenReturn(List.of(socket));
        when(access.isLive(socket.principal(), "http-a")).thenReturn(true);
        register(owner, "stomp-a");

        hub.publishAfterCommit(owner, "INPUT", entity, "SUCCEEDED");

        ArgumentCaptor<WorkbenchEvent> event = ArgumentCaptor.forClass(WorkbenchEvent.class);
        verify(messaging).convertAndSendToUser(eq(owner.toString()), eq("/queue/workbench-events"),
                event.capture(), any(org.springframework.messaging.MessageHeaders.class));
        assertThat(event.getValue().entityId()).isEqualTo(entity);
        assertThat(event.getValue().kind()).isEqualTo("INPUT");
        assertThat(event.getValue().state()).isEqualTo("SUCCEEDED");
        assertThat(event.getValue().eventId()).isNotNull();
        verify(sockets, never()).forUser(other);
    }

    @Test void waitsForCommitAndSkipsRevokedSessions() throws Exception {
        UUID owner = UUID.randomUUID();
        WorkbenchSocketSessions.SocketIdentity valid = socket(owner, "http-valid", "stomp-valid");
        WorkbenchSocketSessions.SocketIdentity revoked = socket(owner, "http-revoked", "stomp-revoked");
        when(sockets.forUser(owner)).thenReturn(List.of(valid, revoked));
        when(access.isLive(valid.principal(), valid.httpSessionId())).thenReturn(true);
        register(owner, "stomp-valid");
        TransactionSynchronizationManager.initSynchronization();
        try {
            hub.publishAfterCommit(owner, "REPORT", UUID.randomUUID(), "PROCESSING");
            verify(messaging, never()).convertAndSendToUser(any(), any(), any(), any(org.springframework.messaging.MessageHeaders.class));
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(messaging).convertAndSendToUser(eq(owner.toString()), eq("/queue/workbench-events"),
                any(WorkbenchEvent.class), any(org.springframework.messaging.MessageHeaders.class));
        verify(sockets).close(revoked);
    }

    @Test void notificationFailureDoesNotEscapeCommittedWrite() throws Exception {
        UUID owner = UUID.randomUUID();
        WorkbenchSocketSessions.SocketIdentity socket = socket(owner, "http-a", "stomp-a");
        when(sockets.forUser(owner)).thenReturn(List.of(socket));
        when(access.isLive(socket.principal(), socket.httpSessionId())).thenThrow(new IllegalStateException("Redis down"));
        assertThatCode(() -> hub.publishAfterCommit(owner, "INPUT", UUID.randomUUID(), "SUCCEEDED"))
                .doesNotThrowAnyException();
        verify(sockets).close(socket);
        verify(messaging, never()).convertAndSendToUser(any(), any(), any(), any(org.springframework.messaging.MessageHeaders.class));
    }

    @Test void rolledBackTransactionNeverNotifies() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            hub.publishAfterCommit(UUID.randomUUID(), "INPUT", UUID.randomUUID(), "SUCCEEDED");
            // Rollback clears registered synchronizations without invoking afterCommit.
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(messaging, never()).convertAndSendToUser(any(), any(), any(), any(org.springframework.messaging.MessageHeaders.class));
    }

    private static WorkbenchSocketSessions.SocketIdentity socket(UUID owner, String httpId, String stompId)
            throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(stompId);
        when(session.isOpen()).thenReturn(true);
        return new WorkbenchSocketSessions.SocketIdentity(session, httpId,
                new WorkbenchPrincipal(owner, "USER", 1));
    }

    private void register(UUID owner, String stompId) {
        SimpUser user = mock(SimpUser.class);
        when(users.getUser(owner.toString())).thenReturn(user);
        when(user.getSession(stompId)).thenReturn(mock(SimpSession.class));
    }
}
