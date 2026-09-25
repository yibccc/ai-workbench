package com.aiworkbench.service.impl;

import com.aiworkbench.ai.AiReportResult;
import com.aiworkbench.ai.ReportAiGateway;
import com.aiworkbench.ai.ReportSourcePrompt;
import com.aiworkbench.common.PageQueries;
import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.report.CreateReportRequest;
import com.aiworkbench.dto.report.ReportClaim;
import com.aiworkbench.dto.report.ReportResponse;
import com.aiworkbench.dto.report.UpdateManualAdditionsRequest;
import com.aiworkbench.dto.report.UpdateReportRequest;
import com.aiworkbench.entity.report.ReportRow;
import com.aiworkbench.entity.report.ReportSourceRow;
import com.aiworkbench.enums.ReportSectionType;
import com.aiworkbench.enums.ReportSourceRole;
import com.aiworkbench.enums.ReportSourceType;
import com.aiworkbench.exception.DeepSeekNotConfiguredException;
import com.aiworkbench.exception.ReportGenerationException;
import com.aiworkbench.mapper.ReportMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.ReportPersistenceService;
import com.aiworkbench.service.ReportService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportServiceImpl implements ReportService {
    private static final int MAX_BULLETS = 50;
    private final ReportMapper mapper;
    private final ReportPersistenceService persistence;
    private final ReportAiGateway aiGateway;
    private final TaskExecutor taskExecutor;
    private final ZoneId zoneId;

    public ReportServiceImpl(ReportMapper mapper, ReportPersistenceService persistence, ReportAiGateway aiGateway,
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
        if (claim.owner()) schedule(claim.row().userId(), claim.row().id(), claim.token(), reportType, periodStart, periodEnd);
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
        return mapper.findReports(CurrentUser.requireId(), normalizedType, periodStart).stream().map(this::toResponse).toList();
    }

    public PageResponse<ReportResponse> page(String reportType, LocalDate date, int page, int size) {
        String normalizedType = normalizeType(reportType);
        LocalDate periodStart = normalizedType.equals("WEEKLY") && date != null
                ? date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) : date;
        UUID userId = CurrentUser.requireId();
        return PageQueries.select(page, size, () -> mapper.findReportPage(userId, normalizedType, periodStart),
                this::toSummaryResponse);
    }

    public PageResponse<ReportResponse.Source> sourcePage(UUID reportId, int page, int size) {
        ReportRow report = persistence.require(reportId);
        return PageQueries.select(page, size, () -> mapper.findSourcePage(report.userId(), reportId), this::toSource);
    }

    private String normalizeType(String reportType) {
        String normalized = reportType == null ? "DAILY" : reportType.toUpperCase(java.util.Locale.ROOT);
        if (!normalized.equals("DAILY") && !normalized.equals("WEEKLY")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报告类型只支持 DAILY 或 WEEKLY");
        }
        return normalized;
    }

    public void delete(UUID id, long version) {
        if (version < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本号不能为负数");
        persistence.delete(id, version, Instant.now());
    }

    public ReportResponse update(UUID id, UpdateReportRequest request) {
        return toResponse(persistence.update(id, request.content().trim(), request.version(), Instant.now()));
    }

    public ReportResponse updateManualAdditions(UUID id, UpdateManualAdditionsRequest request) {
        return toResponse(persistence.updateManualAdditions(id, request.manualAdditions().trim(),
                request.version(), Instant.now()));
    }

    private void schedule(UUID userId, UUID id, UUID token, String reportType, LocalDate periodStart, LocalDate periodEnd) {
        try { taskExecutor.execute(() -> process(userId, id, token, reportType, periodStart, periodEnd)); }
        catch (RuntimeException exception) {
            persistence.fail(id, token, "报告生成暂时不可用，请稍后重试", "MODEL_UNAVAILABLE",
                    "SCHEDULE", Math.toIntExact(mapper.countSources(userId, id)), Instant.now());
        }
    }

    private void process(UUID userId, UUID id, UUID token, String reportType, LocalDate periodStart, LocalDate periodEnd) {
        persistence.requireOwned(userId, id);
        List<ReportSourceRow> sources = mapper.findSources(userId, id);
        try {
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
            Failure failure = classify(exception, reportType, sources.size());
            persistence.fail(id, token, failure.message(), failure.code(), failure.stage(),
                    sources.size(), Instant.now());
        }
    }

    Map<ReportSectionType, List<ValidatedBullet>> validate(AiReportResult result, List<ReportSourceRow> sources) {
        return validate(result, sources, "DAILY");
    }

    Map<ReportSectionType, List<ValidatedBullet>> validate(AiReportResult result, List<ReportSourceRow> sources,
                                                            String reportType) {
        if (result == null || result.sections() == null) throw failure(
                ReportGenerationException.Code.INVALID_JSON, "VALIDATE", "模型结果缺少 sections");
        Map<UUID, ReportSourceRow> byId = new java.util.HashMap<>();
        sources.forEach(source -> byId.put(source.id(), source));
        Map<ReportSectionType, List<ValidatedBullet>> output = new EnumMap<>(ReportSectionType.class);
        int count = 0;
        for (AiReportResult.Section section : result.sections()) {
            ReportSectionType type;
            try { type = ReportSectionType.valueOf(section.type()); }
            catch (RuntimeException exception) { throw new ReportGenerationException(
                    ReportGenerationException.Code.INVALID_BULLET, "VALIDATE", "模型返回未知报告分段", exception); }
            if (output.containsKey(type)) throw failure(ReportGenerationException.Code.INVALID_BULLET,
                    "VALIDATE", "模型返回重复报告分段");
            if (section.bullets() == null) throw failure(ReportGenerationException.Code.INVALID_BULLET,
                    "VALIDATE", "报告分段缺少 bullets");
            List<ValidatedBullet> bullets = new ArrayList<>();
            for (AiReportResult.Bullet bullet : section.bullets()) {
                count++;
                if (count > MAX_BULLETS) throw failure(ReportGenerationException.Code.INVALID_BULLET,
                        "VALIDATE", "报告要点过多");
                String text = bullet.text() == null ? "" : bullet.text().trim();
                if (text.isEmpty() || text.length() > 1000) throw failure(
                        ReportGenerationException.Code.INVALID_BULLET, "VALIDATE", "报告要点文本无效");
                if (bullet.sourceIds() == null || bullet.sourceIds().isEmpty()) throw failure(
                        ReportGenerationException.Code.INVALID_BULLET, "VALIDATE", "报告要点缺少来源");
                LinkedHashSet<UUID> local = new LinkedHashSet<>();
                for (UUID sourceId : bullet.sourceIds()) {
                    if (sourceId == null) throw failure(ReportGenerationException.Code.UNKNOWN_SOURCE,
                            "VALIDATE", "报告引用了空来源");
                    if (!local.add(sourceId)) continue;
                    ReportSourceRow source = byId.get(sourceId);
                    if (source == null) throw failure(ReportGenerationException.Code.UNKNOWN_SOURCE,
                            "VALIDATE", "报告引用了未知来源");
                    if (!roleAllowed(reportType, type, source)) {
                        throw failure(ReportGenerationException.Code.SOURCE_ROLE_MISMATCH,
                                "VALIDATE", "报告来源超出分段事实边界");
                    }
                }
                bullets.add(new ValidatedBullet(text, List.copyOf(local)));
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

    private ReportGenerationException failure(ReportGenerationException.Code code, String stage, String detail) {
        return new ReportGenerationException(code, stage, detail);
    }

    private Failure classify(RuntimeException exception, String reportType, int sourceCount) {
        String label = reportType.equals("WEEKLY") ? "周报" : "日报";
        if (exception instanceof ReportGenerationException failure) {
            return new Failure(failure.code().name(), failure.stage(),
                    label + "生成失败（" + failure.code().name() + "），请检查来源后重新生成");
        }
        if (exception instanceof DeepSeekNotConfiguredException) {
            return new Failure("MODEL_UNAVAILABLE", "MODEL_CALL", "DeepSeek 尚未配置");
        }
        if (isTimeout(exception)) {
            return new Failure("MODEL_TIMEOUT", "MODEL_CALL",
                    label + "生成超时，已保留 " + sourceCount + " 个来源快照，可稍后手动重新生成");
        }
        return new Failure("MODEL_UNAVAILABLE", "MODEL_CALL", label + "生成失败，可稍后手动重新生成");
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String type = current.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            String message = current.getMessage() == null ? ""
                    : current.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (type.contains("timeout") || message.contains("timeout") || message.contains("timed out")) {
                return true;
            }
        }
        return false;
    }

    private ReportResponse toResponse(ReportRow row) {
        List<ReportResponse.Source> sources = mapper.findSources(row.userId(), row.id()).stream().map(this::toSource).toList();
        return response(row, sources);
    }

    private ReportResponse toSummaryResponse(ReportRow row) { return response(row, List.of()); }

    private ReportResponse response(ReportRow row, List<ReportResponse.Source> sources) {
        return new ReportResponse(row.id(), row.requestId(), row.reportType(), row.periodStart(), row.periodEnd(), row.status(),
                row.content(), row.errorMessage(), row.zoneId(), row.version(), row.editedAt(), row.previousReportId(),
                row.manualAdditions(), row.manualEditedAt(), row.createdAt(), row.updatedAt(), row.errorCode(),
                row.errorStage(), row.sourceCount(), sources);
    }

    private ReportResponse.Source toSource(ReportSourceRow source) {
        return new ReportResponse.Source(source.id(), source.sourceType(), source.sourceRole(), source.entityId(),
                source.content(), source.projectId(), source.projectName(), source.sourceStatus(), source.sourceTime());
    }

    record ValidatedBullet(String text, List<UUID> sourceIds) {}
    private record Failure(String code, String stage, String message) {}
}
