package com.aiworkbench.events;

import com.aiworkbench.security.SessionAccess;
import com.aiworkbench.security.WorkbenchAuthentications;
import com.aiworkbench.security.WorkbenchPrincipal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkbenchStompGuardTest {
    private final SessionAccess access = mock(SessionAccess.class);
    private final WorkbenchSocketSessions sockets = mock(WorkbenchSocketSessions.class);
    private final WorkbenchStompGuard guard = new WorkbenchStompGuard(access, sockets);
    private final WorkbenchPrincipal user = new WorkbenchPrincipal(UUID.randomUUID(), "USER", 1);

    @Test void connectRequiresMatchingCsrfAndLiveSession() {
        when(access.isLive(user, "http-a")).thenReturn(true);
        assertThatCode(() -> guard.preSend(frame(StompCommand.CONNECT, null, "secret", user), mock(org.springframework.messaging.MessageChannel.class)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.CONNECT, null, "wrong", user),
                mock(org.springframework.messaging.MessageChannel.class))).isInstanceOf(AccessDeniedException.class);
        verify(access, org.mockito.Mockito.times(2)).isLive(user, "http-a");
    }

    @Test void onlyOwnUserDestinationCanBeSubscribedAndClientSendIsRejected() {
        when(access.isLive(user, "http-a")).thenReturn(true);
        var channel = mock(org.springframework.messaging.MessageChannel.class);
        assertThatCode(() -> guard.preSend(frame(StompCommand.SUBSCRIBE,
                WorkbenchWebSocketConfig.USER_DESTINATION, null, user), channel)).doesNotThrowAnyException();
        for (String destination : new String[]{"/queue/workbench-events", "/user/other/queue/workbench-events",
                "/user/queue/*", "/topic/workbench-events"}) {
            assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, destination, null, user), channel))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SEND,
                WorkbenchWebSocketConfig.USER_DESTINATION, null, user), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void forgedPrincipalAndExpiredSessionAreRejected() {
        var channel = mock(org.springframework.messaging.MessageChannel.class);
        WorkbenchPrincipal other = new WorkbenchPrincipal(UUID.randomUUID(), "USER", 1);
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.CONNECT, null, "secret", other), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SUBSCRIBE,
                WorkbenchWebSocketConfig.USER_DESTINATION, null, user), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> frame(StompCommand command, String destination, String csrf,
                                  WorkbenchPrincipal authenticatedPrincipal) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setSessionId("stomp-a");
        headers.setUser(WorkbenchAuthentications.authentication(authenticatedPrincipal));
        headers.setSessionAttributes(Map.of(WorkbenchWebSocketConfig.HTTP_SESSION_ID, "http-a",
                WorkbenchWebSocketConfig.PRINCIPAL, user,
                WorkbenchWebSocketConfig.CSRF_TOKEN, "secret"));
        if (destination != null) headers.setDestination(destination);
        if (csrf != null) headers.addNativeHeader("X-XSRF-TOKEN", csrf);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
