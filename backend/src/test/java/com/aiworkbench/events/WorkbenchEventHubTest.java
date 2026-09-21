package com.aiworkbench.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkbenchEventHubTest {
    @Test void sendsOnlyCommittedStateMetadata() throws Exception {
        WorkbenchEventHub hub = new WorkbenchEventHub(new ObjectMapper().findAndRegisterModules());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        hub.afterConnectionEstablished(session);
        UUID id = UUID.randomUUID();
        hub.publishAfterCommit("INPUT", id, "SUCCEEDED");
        verify(session).sendMessage(argThat(message -> {
            String payload = ((TextMessage) message).getPayload();
            return payload.contains(id.toString()) && payload.contains("SUCCEEDED")
                    && !payload.contains("content") && !payload.contains("apiKey");
        }));
    }

    @Test void waitsForCommitAndUsesStrictlyIncreasingRevisions() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WorkbenchEventHub hub = new WorkbenchEventHub(mapper);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        hub.afterConnectionEstablished(session);
        UUID id = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        try {
            hub.publishAfterCommit("REPORT", id, "PROCESSING");
            verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        hub.publishAfterCommit("REPORT", id, "SUCCEEDED");

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
        List<Long> revisions = messages.getAllValues().stream().map(message -> {
            try { return mapper.readTree(message.getPayload()).get("revision").asLong(); }
            catch (Exception exception) { throw new AssertionError(exception); }
        }).toList();
        org.assertj.core.api.Assertions.assertThat(revisions.get(1)).isGreaterThan(revisions.get(0));
    }

    @Test void deliveryFailureNeverEscapesToTheCommittedWritePath() throws Exception {
        WorkbenchEventHub hub = new WorkbenchEventHub(new ObjectMapper().findAndRegisterModules());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        doThrow(new java.io.IOException("socket closed")).when(session)
                .sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
        hub.afterConnectionEstablished(session);

        org.assertj.core.api.Assertions.assertThatCode(
                () -> hub.publishAfterCommit("INPUT", UUID.randomUUID(), "SUCCEEDED"))
                .doesNotThrowAnyException();
    }
}
