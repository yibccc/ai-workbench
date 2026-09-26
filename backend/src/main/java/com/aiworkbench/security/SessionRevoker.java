package com.aiworkbench.security;

import com.aiworkbench.events.WorkbenchSocketSessions;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

@Component
public class SessionRevoker {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final SessionActivity activity;
    private final WorkbenchSocketSessions sockets;

    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions, SessionActivity activity,
                          WorkbenchSocketSessions sockets) {
        this.sessions = sessions;
        this.activity = activity;
        this.sockets = sockets;
    }

    /** The DB auth-version check closes the race between commit and this best-effort indexed cleanup. */
    public void revokeOlder(UUID userId, long currentVersion, String preserveSessionId) {
        Map<String, ? extends Session> indexed = sessions.findByPrincipalName(userId.toString());
        for (Session session : indexed.values()) {
            if (session.getId().equals(preserveSessionId)) {
                continue;
            }
            Object context = session.getAttribute("SPRING_SECURITY_CONTEXT");
            if (context instanceof SecurityContext securityContext) {
                Authentication authentication = securityContext.getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof WorkbenchPrincipal principal
                        && principal.authVersion() >= currentVersion) {
                    continue;
                }
            }
            activity.remove(session.getId());
            sockets.closeSession(session.getId());
            sessions.deleteById(session.getId());
        }
    }
}
