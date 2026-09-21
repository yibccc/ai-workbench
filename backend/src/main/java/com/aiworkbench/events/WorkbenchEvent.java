package com.aiworkbench.events;

import java.time.Instant;
import java.util.UUID;

public record WorkbenchEvent(UUID eventId, String kind, UUID entityId, String state,
                             long revision, Instant occurredAt) {}
