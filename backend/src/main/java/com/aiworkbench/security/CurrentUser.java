package com.aiworkbench.security;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static WorkbenchPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof WorkbenchPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("请先登录");
        }
        return principal;
    }

    public static UUID requireId() {
        return require().userId();
    }
}
