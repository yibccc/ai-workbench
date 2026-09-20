package com.aiworkbench.report;

import com.aiworkbench.ai.DeepSeekNotConfiguredException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {
    private static final int MAX_BULLETS = 50;
    private final ReportMapper mapper;
    private final ReportPersistenceService persistence;
    private final ReportAiGateway aiGateway;
    private final TaskExecutor taskExecutor;
    private final ZoneId zoneId;

    public ReportService(ReportMapper mapper, ReportPersistenceService persistence, ReportAiGateway aiGateway,
                         TaskExecutor taskExecutor, @Value("${workbench.zone-id:Asia/Shanghai}") String zoneId) {
        this.mapper = mapper; this.persistence = persistence; this.aiGateway = aiGateway;
        this.taskExecutor = taskExecutor; this.zoneId = ZoneId.of(zoneId);
    }

    public ReportResponse create(CreateReportRequest request) {
        String reportType = request.reportType() == null ? "DAILY" : request.reportType().toUpperCase(java.util.Locale.ROOT);
        if (!reportType.equals("DAILY") && !reportType.equals("WEEKLY"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报告类型只支持 DAILY 或 WEEKLY");
        LocalDate date = request.date() == null ? LocalDate.now(zoneId) : request.date();
        LocalDate periodStart = reportType.equals("WEEKLY")
                ? date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) : date;
        LocalDate periodEnd = reportType.equals("WEEKLY") ? periodStart.plusWeeks(1) : periodStart;
        ReportClaim claim = persistence.prepare(request.requestId(), reportType, periodStart, periodEnd, zoneId);
        if (claim.owner()) schedule(claim.row().id(), claim.token(), reportType, periodStart, periodEnd);
        return get(claim.row().id());
    }

    public ReportResponse get(UUID id) { return toResponse(persistence.require(id)); }

    public List<ReportResponse> list(LocalDate date) {
        return list("DAILY", date);
    }

    public List<ReportResponse> list(String reportType, LocalDate date) {
        String normalizedType = reportType == null ? "DAILY" : reportType.toUpperCase(java.util.Locale.ROOT);
        if (!normalizedType.equals("DAILY") && !normalizedType.equals("WEEKLY"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报告类型只支持 DAILY 或 WEEKLY");
        LocalDate periodStart = date;
        if (date != null && normalizedType.equals("WEEKLY"))
            periodStart = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return mapper.findReports(normalizedType, periodStart).stream().map(this::toResponse).toList();
    }

    public ReportResponse update(UUID id, UpdateReportRequest request) {
        return toResponse(persistence.update(id, request.content().trim(), request.version(), Instant.now()));
    }

    public ReportResponse updateManualAdditions(UUID id, UpdateManualAdditionsRequest request) {
        return toResponse(persistence.updateManualAdditions(id, request.manualAdditions().trim(),
                request.version(), Instant.now()));
    }

    private void schedule(UUID id, UUID token, String reportType, LocalDate periodStart, LocalDate periodEnd) {
        try { taskExecutor.execute(() -> process(id, token, reportType, periodStart, periodEnd)); }
        catch (RuntimeException exception) { persistence.fail(id, token, "报告生成暂时不可用，请稍后重试", Instant.now()); }
    }

    private void process(UUID id, UUID token, String reportType, LocalDate periodStart, LocalDate periodEnd) {
        try {
            List<ReportSourceRow> sources = mapper.findSources(id);
            String content;
            if (sources.isEmpty()) content = render(Map.of(), sources, reportType);
            else {
                List<ReportSourcePrompt> promptSources = sources.stream().map(source -> new ReportSourcePrompt(
                        source.id(), source.sourceType(), source.sourceRole(), source.content(), source.projectName(),
                        source.sourceStatus(), source.sourceTime())).toList();
                AiReportResult generated = reportType.equals("WEEKLY")
                        ? aiGateway.generateWeekly(periodStart, periodEnd, zoneId, promptSources)
                        : aiGateway.generate(periodStart, zoneId, promptSources);
                content = render(validate(generated, sources, reportType), sources, reportType);
            }
            persistence.succeed(id, token, content, Instant.now());
        } catch (RuntimeException exception) {
            persistence.fail(id, token, safeFailure(exception), Instant.now());
        }
    }

    Map<ReportSectionType, List<ValidatedBullet>> validate(AiReportResult result, List<ReportSourceRow> sources) {
        return validate(result, sources, "DAILY");
    }

    Map<ReportSectionType, List<ValidatedBullet>> validate(AiReportResult result, List<ReportSourceRow> sources,
                                                            String reportType) {
        if (result == null || result.sections() == null) throw new IllegalArgumentException("模型结果缺少 sections");
        Map<UUID, ReportSourceRow> byId = new java.util.HashMap<>();
        sources.forEach(source -> byId.put(source.id(), source));
        Map<ReportSectionType, List<ValidatedBullet>> output = new EnumMap<>(ReportSectionType.class);
        Set<UUID> used = new HashSet<>();
        int count = 0;
        for (AiReportResult.Section section : result.sections()) {
            ReportSectionType type;
            try { type = ReportSectionType.valueOf(section.type()); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("模型返回未知日报分段", exception); }
            if (output.containsKey(type)) throw new IllegalArgumentException("模型返回重复日报分段");
            if (section.bullets() == null) throw new IllegalArgumentException("日报分段缺少 bullets");
            List<ValidatedBullet> bullets = new ArrayList<>();
            for (AiReportResult.Bullet bullet : section.bullets()) {
                count++;
                if (count > MAX_BULLETS) throw new IllegalArgumentException("日报要点过多");
                String text = bullet.text() == null ? "" : bullet.text().trim();
                if (text.isEmpty() || text.length() > 1000) throw new IllegalArgumentException("日报要点文本无效");
                if (bullet.sourceIds() == null || bullet.sourceIds().isEmpty()) throw new IllegalArgumentException("日报要点缺少来源");
                Set<UUID> local = new HashSet<>();
                for (UUID sourceId : bullet.sourceIds()) {
                    if (sourceId == null || !local.add(sourceId) || !used.add(sourceId)) {
                        throw new IllegalArgumentException("日报来源存在重复引用");
                    }
                    ReportSourceRow source = byId.get(sourceId);
                    if (source == null) throw new IllegalArgumentException("日报引用了未知来源");
                    if (!roleAllowed(reportType, type, source)) {
                        throw new IllegalArgumentException("日报来源超出分段事实边界");
                    }
                }
                bullets.add(new ValidatedBullet(text, List.copyOf(bullet.sourceIds())));
            }
            output.put(type, bullets);
        }
        return output;
    }

    String render(Map<ReportSectionType, List<ValidatedBullet>> sections, List<ReportSourceRow> sources) {
        return render(sections, sources, "DAILY");
    }

    String render(Map<ReportSectionType, List<ValidatedBullet>> sections, List<ReportSourceRow> sources,
                  String reportType) {
        Map<UUID, Integer> sourceNumbers = new java.util.HashMap<>();
        for (int index = 0; index < sources.size(); index++) sourceNumbers.put(sources.get(index).id(), index + 1);
        boolean weekly = reportType.equals("WEEKLY");
        return renderSection(weekly ? "本周完成" : "明确成果", sections.get(ReportSectionType.ACHIEVEMENTS),
                weekly ? "本周暂无有效完成记录" : "暂无记录", sourceNumbers) + "\n\n"
                + renderSection(weekly ? "进行中与阻碍" : "工作进展", sections.get(ReportSectionType.PROGRESS),
                weekly ? "暂无有依据的进行中事项或阻碍" : "暂无记录", sourceNumbers) + "\n\n"
                + renderSection(weekly ? "下周计划" : "计划", sections.get(ReportSectionType.PLANS),
                weekly ? "暂无明确安排到下周的计划" : "暂无已安排计划", sourceNumbers);
    }

    private boolean roleAllowed(String reportType, ReportSectionType section, ReportSourceRow source) {
        if (!reportType.equals("WEEKLY")) {
            boolean expectedTask = section == ReportSectionType.PLANS;
            return (source.sourceType() == ReportSourceType.TASK) == expectedTask;
        }
        return switch (section) {
            case ACHIEVEMENTS -> source.sourceRole() == ReportSourceRole.WEEK_RECORD;
            case PROGRESS -> source.sourceRole() == ReportSourceRole.WEEK_RECORD
                    || source.sourceRole() == ReportSourceRole.CURRENT_TASK;
            case PLANS -> source.sourceRole() == ReportSourceRole.NEXT_WEEK_TASK;
        };
    }

    private String renderSection(String title, List<ValidatedBullet> bullets, String empty,
                                 Map<UUID, Integer> sourceNumbers) {
        if (bullets == null || bullets.isEmpty()) return "## " + title + "\n- " + empty;
        return "## " + title + "\n" + bullets.stream().map(bullet -> {
            String references = bullet.sourceIds().stream().map(sourceNumbers::get).map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining("、"));
            return "- " + bullet.text() + " [来源 " + references + "]";
        }).collect(java.util.stream.Collectors.joining("\n"));
    }

    private String safeFailure(RuntimeException exception) {
        if (exception instanceof DeepSeekNotConfiguredException) return "DeepSeek 尚未配置";
        if (exception instanceof IllegalArgumentException) return "AI 日报未通过来源校验，请重新生成";
        return "AI 报告生成失败，可稍后重新生成（" + exception.getClass().getSimpleName() + "）";
    }

    private ReportResponse toResponse(ReportRow row) {
        List<ReportResponse.Source> sources = mapper.findSources(row.id()).stream().map(source ->
                new ReportResponse.Source(source.id(), source.sourceType(), source.sourceRole(), source.entityId(), source.content(),
                        source.projectId(), source.projectName(), source.sourceStatus(), source.sourceTime())).toList();
        return new ReportResponse(row.id(), row.requestId(), row.reportType(), row.periodStart(), row.periodEnd(), row.status(),
                row.content(), row.errorMessage(), row.zoneId(), row.version(), row.editedAt(), row.previousReportId(),
                row.manualAdditions(), row.manualEditedAt(), row.createdAt(), row.updatedAt(), sources);
    }

    record ValidatedBullet(String text, List<UUID> sourceIds) {}
}
