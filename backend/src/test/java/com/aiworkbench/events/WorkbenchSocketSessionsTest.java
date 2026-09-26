package com.aiworkbench.events;

import com.aiworkbench.security.WorkbenchPrincipal;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkbenchSocketSessionsTest {
    @Test
    void transportCloseFailureDoesNotFailAnAlreadyCommittedAccountChange() throws IOException {
        WebSocketSession transport = mock(WebSocketSession.class);
        when(transport.getId()).thenReturn("stomp-a");
        when(transport.isOpen()).thenReturn(true);
        doThrow(new IllegalStateException("transport already closing"))
                .when(transport).close(CloseStatus.POLICY_VIOLATION);
        WorkbenchSocketSessions sessions = new WorkbenchSocketSessions();
        var identity = new WorkbenchSocketSessions.SocketIdentity(transport, "http-a",
                new WorkbenchPrincipal(UUID.randomUUID(), "USER", 1));

        assertThatCode(() -> sessions.close(identity)).doesNotThrowAnyException();
    }
}
