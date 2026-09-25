package com.aiworkbench.security;

import com.aiworkbench.entity.account.AccountRow;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class WorkbenchAuthentications {
    private WorkbenchAuthentications() {
    }

    public static WorkbenchPrincipal principal(AccountRow account) {
        return new WorkbenchPrincipal(account.id(), account.role(), account.authVersion());
    }

    public static Authentication authentication(WorkbenchPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
    }
}
