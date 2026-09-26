package com.aiworkbench.entity.report;

import com.aiworkbench.enums.ReportSourceRole;
import com.aiworkbench.enums.ReportSourceType;
import java.time.Instant;
import java.util.UUID;

public record ReportSourceRow(UUID id, UUID reportId, ReportSourceType sourceType, ReportSourceRole sourceRole, UUID entityId,
                       String content, UUID projectId, String projectName, String sourceStatus,
                       Instant sourceTime, String snapshot) {
    public ReportSourceRow(UUID id, UUID reportId, ReportSourceType sourceType, UUID entityId,
                    String content, UUID projectId, String projectName, String sourceStatus,
                    Instant sourceTime, String snapshot) {
        this(id, reportId, sourceType,
                sourceType == ReportSourceType.RECORD ? ReportSourceRole.DAILY_RECORD : ReportSourceRole.DAILY_TASK,
                entityId, content, projectId, projectName, sourceStatus, sourceTime, snapshot);
    }
    private com.fasterxml.jackson.databind.JsonNode parsed() {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readTree(snapshot == null ? "{}" : snapshot); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(); }
    }
    public UUID taskId() { return uuid("taskId"); }
    public UUID sessionId() { return uuid("sessionId"); }
    private UUID uuid(String field) {
        String value=parsed().path(field).asText("");
        try { return value.isEmpty() ? null : UUID.fromString(value); } catch (IllegalArgumentException exception) { return null; }
    }
    public java.time.LocalDate businessDate() {
        String value=parsed().path("businessDate").asText("");
        try { return value.isEmpty() ? null : java.time.LocalDate.parse(value); } catch (java.time.format.DateTimeParseException exception) { return null; }
    }
    public Long focusMs() { return parsed().path("focusMs").isNumber() ? parsed().path("focusMs").asLong() : null; }
    public Long breakMs() { return parsed().path("breakMs").isNumber() ? parsed().path("breakMs").asLong() : null; }
    public String progress() { return parsed().path("progress").isTextual() ? parsed().path("progress").asText() : null; }
}
