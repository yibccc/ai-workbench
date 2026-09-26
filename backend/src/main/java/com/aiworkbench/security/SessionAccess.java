package com.aiworkbench.security;

import com.aiworkbench.entity.account.AccountRow;
import com.aiworkbench.service.AccountService;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

/** Shared HTTP/STOMP contract: DB version and explicit activity marker must both be valid. */
@Component
public class SessionAccess {
    private final AccountService accounts;
    private final SessionActivity activity;

    public SessionAccess(AccountService accounts, SessionActivity activity) {
        this.accounts = accounts;
        this.activity = activity;
    }

    public boolean isLive(WorkbenchPrincipal principal, String sessionId) {
        if (principal == null || sessionId == null || !activity.isLive(sessionId)) {
            return false;
        }
        AccountRow account = accounts.findById(principal.userId());
        return account != null && account.enabled() && account.authVersion() == principal.authVersion()
                && account.role().equals(principal.role());
    }

    public void requireLive(WorkbenchPrincipal principal, String sessionId) {
        if (!isLive(principal, sessionId)) {
            throw new AuthenticationCredentialsNotFoundException("会话已失效，请重新登录");
        }
    }
}
