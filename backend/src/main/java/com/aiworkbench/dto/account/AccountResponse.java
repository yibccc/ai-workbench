package com.aiworkbench.dto.account;

import com.aiworkbench.entity.account.AccountRow;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String username, String role, boolean enabled, Instant createdAt) {
    public static AccountResponse from(AccountRow row) {
        return new AccountResponse(row.id(), row.username(), row.role(), row.enabled(), row.createdAt());
    }
}
