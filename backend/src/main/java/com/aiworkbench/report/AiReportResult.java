package com.aiworkbench.report;

import java.util.List;
import java.util.UUID;

public record AiReportResult(List<Section> sections) {
    public record Section(String type, List<Bullet> bullets) {}
    public record Bullet(String text, List<UUID> sourceIds) {}
}
