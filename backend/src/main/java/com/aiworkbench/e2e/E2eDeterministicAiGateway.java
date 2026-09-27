package com.aiworkbench.e2e;

import com.aiworkbench.ai.AiCaptureResult;
import com.aiworkbench.ai.AiReportResult;
import com.aiworkbench.ai.DeepSeekGateway;
import com.aiworkbench.ai.ReportAiGateway;
import com.aiworkbench.ai.ReportSourcePrompt;
import com.aiworkbench.ai.WorkbenchAiGateway;
import com.aiworkbench.enums.ReportSectionType;
import com.aiworkbench.enums.ReportSourceRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Deterministic model substitute for automated tests. This bean can only be
 * activated by isolated test profiles and never performs network I/O.
 */
@Component
@Profile("e2e & !live-acceptance")
public class E2eDeterministicAiGateway implements WorkbenchAiGateway, ReportAiGateway, DeepSeekGateway {
    private final Set<String> failedOnce = ConcurrentHashMap.newKeySet();

    public void reset() {
        failedOnce.clear();
    }

    @Override
    public String probe() {
        return "AI_WORKBENCH_E2E_OK";
    }

    @Override
    public AiCaptureResult extract(String content, Instant referenceAt, ZoneId zoneId,
                                   List<String> activeProjectNames) {
        if (content.contains("[E2E_DELAY_9S]")) {
            try { Thread.sleep(9_000); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        }
        if (content.contains("[E2E_FAIL_ALWAYS]")) throw new IllegalStateException("deterministic failure");
        if (content.contains("[E2E_FAIL_ONCE]") && failedOnce.add(content)) {
            throw new IllegalStateException("deterministic first-attempt failure");
        }
        if (content.contains("[E2E_EMPTY]")) return new AiCaptureResult(List.of(), List.of());

        String knownProject = activeProjectNames.stream().filter(name -> name.equals("E2E 项目")).findFirst().orElse(null);
        String project = content.contains("[E2E_UNKNOWN_PROJECT]") ? "不存在的项目" : knownProject;
        Instant occurredAt = content.contains("[E2E_YESTERDAY]") ? referenceAt.minusSeconds(86_400) : referenceAt;
        List<AiCaptureResult.RecordItem> records = new ArrayList<>();
        List<AiCaptureResult.TaskItem> tasks = new ArrayList<>();
        if (!content.contains("[E2E_TASK_ONLY]")) {
            records.add(new AiCaptureResult.RecordItem("完成支付接口联调", project, occurredAt.toString()));
            if (content.contains("[E2E_MULTI]")) {
                records.add(new AiCaptureResult.RecordItem("修复长文本展示问题", knownProject,
                        occurredAt.plusSeconds(60).toString()));
            }
        }
        if (!content.contains("[E2E_RECORD_ONLY]")) {
            tasks.add(new AiCaptureResult.TaskItem("补充回归测试", "覆盖失败、重试与持久化", project,
                    referenceAt.plusSeconds(86_400).toString(),
                    content.contains("[E2E_UNKNOWN_PRIORITY]") ? "URGENT" : "HIGH"));
            if (content.contains("[E2E_MULTI]")) {
                tasks.add(new AiCaptureResult.TaskItem("整理验收证据", "", knownProject,
                        referenceAt.plusSeconds(172_800).toString(), "MEDIUM"));
            }
        }
        return new AiCaptureResult(records, tasks);
    }

    @Override
    public AiReportResult generate(LocalDate date, ZoneId zoneId, List<ReportSourcePrompt> sources) {
        return report(sources, false);
    }

    @Override
    public AiReportResult generateWeekly(LocalDate periodStart, LocalDate periodEnd, ZoneId zoneId,
                                         List<ReportSourcePrompt> sources) {
        return report(sources, true);
    }

    private AiReportResult report(List<ReportSourcePrompt> sources, boolean weekly) {
        List<AiReportResult.Section> sections = new ArrayList<>();
        addSection(sections, ReportSectionType.ACHIEVEMENTS, sources.stream().filter(source -> weekly
                ? source.role() == ReportSourceRole.WEEK_RECORD && !"FOCUS_SESSION".equals(source.status())
                : source.type().name().equals("RECORD") && !"FOCUS_SESSION".equals(source.status())).findFirst().orElse(null));
        addSection(sections, ReportSectionType.PROGRESS, sources.stream().filter(source -> weekly
                ? source.role() == ReportSourceRole.CURRENT_TASK || "FOCUS_SESSION".equals(source.status())
                : "FOCUS_SESSION".equals(source.status())).findFirst().orElse(null));
        addSection(sections, ReportSectionType.PLANS, sources.stream().filter(source -> weekly
                ? source.role() == ReportSourceRole.NEXT_WEEK_TASK
                : source.type().name().equals("TASK")).findFirst().orElse(null));
        return new AiReportResult(sections);
    }

    private void addSection(List<AiReportResult.Section> sections, ReportSectionType type,
                            ReportSourcePrompt source) {
        if (source == null) return;
        sections.add(new AiReportResult.Section(type.name(), List.of(
                new AiReportResult.Bullet(source.content(), List.of(source.id())))));
    }
}
