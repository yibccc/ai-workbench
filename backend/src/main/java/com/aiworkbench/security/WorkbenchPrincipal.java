package com.aiworkbench.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/** The session identity uses an immutable account id, never a mutable display name. */
public record WorkbenchPrincipal(UUID userId, String role, long authVersion) implements Principal, Serializable {
    @Override
    public String getName() {
        return userId.toString();
    }
}
