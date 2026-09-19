package com.aiworkbench.task;

import java.util.UUID;

public record TaskProjectResponse(UUID id, String name, String status) {
}
