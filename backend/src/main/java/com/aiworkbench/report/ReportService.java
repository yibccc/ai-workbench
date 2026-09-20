package com.aiworkbench.report;

import com.aiworkbench.ai.DeepSeekNotConfiguredException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
        if (request.reportType() != null && !request.reportType().equalsIgnoreCase("DAILY")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前只支持 DAILY 日报");
        }
        LocalDate date = request.date() == null ? LocalDate.now(zoneId) : request.date();
        ReportClaim claim = persistence.prepare(request.requestId(), date, zoneId);
        if (claim.owner()) schedule(claim.row().id(), claim.token(), date);
        return get(claim.row().id());
    }

    public ReportResponse get(UUID id) { return toResponse(persistence.require(id)); }

    public List<ReportResponse> list(LocalDate date) {
        return mapper.findDaily(date).stream().map(this::toResponse).toList();
    }

    public ReportResponse update(UUID id, UpdateReportRequest request) {
        return toResponse(persistence.update(id, request.content().trim(), request.version(), Instant.now()));
    }

    private void schedule(UUID id, UUID token, LocalDate date) {
        try { taskExecutor.execute(() -> process(id, token, date)); }
        catch (RuntimeException exception) { persistence.fail(id, token, "日报生成暂时不可用，请稍后重试", Instant.now()); }
    }

    private void process(UUID id, UUID token, LocalDate date) {
        try {
            List<ReportSourceRow> sources = mapper.findSources(id);
            String content;
            if (sources.isEmpty()) content = render(Map.of(), sources);
            else {
                List<ReportSourcePrompt> promptSources = sources.stream().map(source -> new ReportSourcePrompt(
                        source.id(), source.sourceType(), source.content(), source.projectName(),
                        source.sourceStatus(), source.sourceTime())).toList();
                content = render(validate(aiGateway.generate(date, zoneId, promptSources), sources), sources);
            }
            persistence.succeed(id, token, content, Instant.now());
        } catch (RuntimeException exception) {
            persistence.fail(id, token, safeFailure(exception), Instant.now());
        }
    }

    Map<ReportSectionType, List<ValidatedBullet>> validate(AiReportResult result, List<ReportSourceRow> sources) {
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
                    boolean expectedTask = type == ReportSectionType.PLANS;
                    if ((source.sourceType() == ReportSourceType.TASK) != expectedTask) {
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
        Map<UUID, Integer> sourceNumbers = new java.util.HashMap<>();
        for (int index = 0; index < sources.size(); index++) sourceNumbers.put(sources.get(index).id(), index + 1);
        return renderSection("明确成果", sections.get(ReportSectionType.ACHIEVEMENTS), "暂无记录", sourceNumbers) + "\n\n"
                + renderSection("工作进展", sections.get(ReportSectionType.PROGRESS), "暂无记录", sourceNumbers) + "\n\n"
                + renderSection("计划", sections.get(ReportSectionType.PLANS), "暂无已安排计划", sourceNumbers);
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
        return "AI 日报生成失败，可稍后重新生成（" + exception.getClass().getSimpleName() + "）";
    }

    private ReportResponse toResponse(ReportRow row) {
        List<ReportResponse.Source> sources = mapper.findSources(row.id()).stream().map(source ->
                new ReportResponse.Source(source.id(), source.sourceType(), source.entityId(), source.content(),
                        source.projectId(), source.projectName(), source.sourceStatus(), source.sourceTime())).toList();
        return new ReportResponse(row.id(), row.requestId(), row.reportType(), row.periodStart(), row.status(),
                row.content(), row.errorMessage(), row.zoneId(), row.version(), row.editedAt(), row.createdAt(), row.updatedAt(), sources);
    }

    record ValidatedBullet(String text, List<UUID> sourceIds) {}
}
