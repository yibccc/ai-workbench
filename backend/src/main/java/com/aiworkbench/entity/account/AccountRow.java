package com.aiworkbench.entity.account;

import java.time.Instant;
import java.util.UUID;

public record AccountRow(UUID id, String username, String passwordHash, String role,
                         Boolean enabled, Long authVersion, Instant createdAt) {
}
