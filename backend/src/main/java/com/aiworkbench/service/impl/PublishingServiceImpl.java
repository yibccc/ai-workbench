package com.aiworkbench.service.impl;

import com.aiworkbench.common.PageQueries;
import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.community.CommunityModels.Author;
import com.aiworkbench.dto.community.CommunityModels.PostDetail;
import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.entity.community.CommunityRows;
import com.aiworkbench.entity.record.WorkRecordRow;
import com.aiworkbench.enums.CommunityPostStatus;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.enums.WorkRecordSource;
import com.aiworkbench.events.WorkbenchEventHub;
import com.aiworkbench.mapper.CommunityPostMapper;
import com.aiworkbench.mapper.AttachmentMapper;
import com.aiworkbench.mapper.CommunityProfileMapper;
import com.aiworkbench.mapper.WorkRecordMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.PublishingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublishingServiceImpl implements PublishingService {
    private final CommunityPostMapper posts;
    private final CommunityProfileMapper profiles;
    private final WorkRecordMapper records;
    private final WorkbenchEventHub events;
    private final ObjectMapper json;
    private final ZoneId zone;
    private final AttachmentMapper attachments;
    private final AttachmentPersistenceService attachmentPersistence;

    public PublishingServiceImpl(CommunityPostMapper posts, CommunityProfileMapper profiles,
            WorkRecordMapper records, WorkbenchEventHub events, ObjectMapper json,
            AttachmentMapper attachments, AttachmentPersistenceService attachmentPersistence,
            @Value("${workbench.zone-id:Asia/Shanghai}") String zone) {
        this.posts = posts;
        this.profiles = profiles;
        this.records = records;
        this.events = events;
        this.json = json;
        this.zone = ZoneId.of(zone);
        this.attachments = attachments;
        this.attachmentPersistence = attachmentPersistence;
    }

    @Transactional
    public OwnerPost create(CreatePost request) {
        UUID owner = CurrentUser.requireId();
        Input input = normalize(request.type(), request.businessDate(), request.title(), request.summary(), request.bodyMarkdown());
        return create(owner, input, "[]");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OwnerPost get(UUID postId) {
        UUID owner = CurrentUser.requireId();
        var post = requireOwned(owner, postId, false);
        var draft = requireDraft(owner, postId);
        PostDetail current = null;
        if (post.currentRevisionId() != null) {
            var row = posts.findRevision(owner, postId, post.currentRevisionId()).orElseThrow();
            var author = profiles.findAuthor(owner).orElseThrow();
            current = new PostDetail(postId, row.type(), row.businessDate(), row.title(), row.summary(), row.bodyMarkdown(),
                    post.firstPublishedAt(), row.publishedAt(), row.id(), row.revisionNo(),
                    new Author(author.id(), author.nickname(), author.bio()), attachments.findRevision(owner, postId, row.id()).stream().map(a -> a.info()).toList());
        }
        var moderation = posts.findModeration(owner, postId).map(row -> new Moderation(row.reason(), row.occurredAt())).orElse(null);
        return new OwnerPost(postId, post.type(), post.status(), post.version(),
                new Draft(draft.type(), draft.businessDate(), draft.title(), draft.summary(), draft.bodyMarkdown(),
                        draft.savedAt(), attachments.findDraft(owner, postId).stream().map(a -> a.id()).toList(),
                        attachments.findDraft(owner, postId).stream().map(a -> a.info()).toList()), current, moderation,
                attachments.findAll(owner, postId).stream().map(a -> a.info()).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<OwnerPostCard> page(CommunityPostStatus status, CommunityPostType type, int page, int size) {
        UUID owner = CurrentUser.requireId();
        return PageQueries.select(page, size, () -> posts.findOwnedPage(owner, status, type), row ->
                new OwnerPostCard(row.postId(), row.type(), row.status(), row.version(), row.businessDate(), row.title(),
                        row.summary(), row.updatedAt(), row.firstPublishedAt(), row.currentRevisionId(), row.currentRevisionNo(),
                        row.hasUnpublishedChanges(), row.moderatedAt() == null ? null : new Moderation(row.moderationReason(), row.moderatedAt())));
    }

    @Transactional
    public Saved save(UUID postId, SaveDraft request) {
        UUID owner = CurrentUser.requireId();
        var post = requireOwned(owner, postId, true);
        requireVersion(post, request.version());
        requireType(post, request.type());
        Input input = normalize(request.type(), request.businessDate(), request.title(), request.summary(), request.bodyMarkdown());
        List<UUID> ids = attachmentIds(request.attachmentIds());
        attachmentPersistence.replaceDraft(post, input.bodyMarkdown(), ids);
        var old = requireDraft(owner, postId);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        writeDraft(post, input, old.sourceSelection(), now);
        updateAggregate(post, post.status(), post.currentRevisionId(), post.firstPublishedAt(), now);
        events.publishAfterCommit(owner, "POST", postId, "SAVED");
        return new Saved(postId, post.version() + 1, now);
    }

    @Transactional
    public Published publish(UUID postId, PublishPost request) {
        UUID owner = CurrentUser.requireId();
        var post = requireOwned(owner, postId, true);
        if (!"MEMBERS".equals(request.visibility())) {
            throw badRequest("发布范围必须为本站登录成员");
        }
        Input input = normalize(request.type(), request.businessDate(), request.title(), request.summary(), request.bodyMarkdown());
        List<UUID> ids = attachmentIds(request.attachmentIds());
        var normalized = new PublishPost(request.requestId(), request.version(), request.visibility(), input.type(),
                input.businessDate(), input.title(), input.summary(), input.bodyMarkdown(), ids);
        String fingerprint = fingerprint(normalized);
        var previous = posts.findPublishResult(owner, postId, request.requestId());
        if (previous.isPresent()) {
            var revision = previous.get();
            if (!revision.requestFingerprint().equals(fingerprint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同一发布请求不能用于不同内容");
            }
            // A receipt is a past result, never an instruction to restore lifecycle or current draft.
            return published(revision, post.firstPublishedAt());
        }
        requireVersion(post, request.version());
        requireType(post, input.type());
        if (post.status() == CommunityPostStatus.HIDDEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "内容已被下架，不能再次发布");
        }
        if (input.bodyMarkdown().isBlank() || (post.type() != CommunityPostType.MOMENT && input.title().isBlank())) {
            throw badRequest("请填写发布正文及所需标题");
        }
        attachmentPersistence.replaceDraft(post, input.bodyMarkdown(), ids);
        var old = requireDraft(owner, postId);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long resultVersion = post.version() + 1;
        var revision = new CommunityRows.Revision(UUID.randomUUID(), postId, owner, posts.nextRevisionNo(owner, postId),
                input.type(), input.businessDate(), input.title(), input.summary(), input.bodyMarkdown(), old.sourceSelection(),
                request.requestId(), fingerprint, resultVersion, now);
        writeDraft(post, input, old.sourceSelection(), now);
        posts.insertRevision(revision);
        attachmentPersistence.bindRevision(post, revision.id(), ids);
        Instant first = post.firstPublishedAt() == null ? now : post.firstPublishedAt();
        updateAggregate(post, CommunityPostStatus.PUBLISHED, revision.id(), first, now);
        events.publishAfterCommit(owner, "POST", postId, "PUBLISHED");
        return published(revision, first);
    }

    @Transactional
    public State withdraw(UUID postId, Version request) {
        UUID owner = CurrentUser.requireId();
        var post = requireOwned(owner, postId, true);
        requireVersion(post, request.version());
        if (post.status() != CommunityPostStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前内容不能撤回");
        }
        updateAggregate(post, CommunityPostStatus.WITHDRAWN, post.currentRevisionId(), post.firstPublishedAt(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        events.publishAfterCommit(owner, "POST", postId, "WITHDRAWN");
        return new State(postId, CommunityPostStatus.WITHDRAWN, post.version() + 1);
    }

    @Transactional(readOnly = true)
    public PageResponse<Material> sources(LocalDate date, int page, int size) {
        if (date == null) throw badRequest("请选择素材日期");
        UUID owner = CurrentUser.requireId();
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        return PageQueries.select(page, size, () -> records.findPageBetween(owner, start, end), row ->
                new Material(row.id(), row.source(), row.content(), text(row.completionResult()), text(row.progress()),
                        row.focusMs(), row.projectName(), row.occurredAt()));
    }

    @Transactional
    public OwnerPost shareDraft(ShareDraft request) {
        UUID owner = CurrentUser.requireId();
        if (request.date() == null || request.selections() == null || request.selections().isEmpty()) {
            throw badRequest("请选择日期和需要分享的素材");
        }
        LinkedHashMap<UUID, LinkedHashSet<MaterialField>> selected = new LinkedHashMap<>();
        for (Selection selection : request.selections()) {
            if (selection == null || selection.recordId() == null || selection.fields() == null
                    || selection.fields().isEmpty() || selection.fields().stream().anyMatch(Objects::isNull)) {
                throw badRequest("素材选择无效");
            }
            selected.computeIfAbsent(selection.recordId(), ignored -> new LinkedHashSet<>()).addAll(selection.fields());
        }
        Instant start = request.date().atStartOfDay(zone).toInstant();
        Instant end = request.date().plusDays(1).atStartOfDay(zone).toInstant();
        var candidates = records.findPresentationByIds(owner, start, end, new ArrayList<>(selected.keySet())).stream()
                .collect(Collectors.toMap(WorkRecordRow::id, Function.identity()));
        for (UUID id : selected.keySet()) {
            if (!candidates.containsKey(id)) {
                if (records.existsOwned(owner, id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "素材状态或日期已变化，请重新选择");
                }
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "素材不存在");
            }
        }
        List<WorkRecordRow> raw = records.findBetween(owner, start, end);
        LinkedHashMap<UUID, Long> focus = new LinkedHashMap<>();
        List<SourceSnapshot> snapshots = new ArrayList<>();
        List<String> excerpts = new ArrayList<>();
        for (var entry : selected.entrySet()) {
            WorkRecordRow row = candidates.get(entry.getKey());
            List<String> parts = new ArrayList<>();
            for (MaterialField field : entry.getValue()) {
                String value = switch (field) {
                    case CONTENT -> row.content();
                    case COMPLETION_RESULT -> row.completionResult();
                    case PROGRESS -> row.progress();
                };
                if (value != null && !value.isBlank()) parts.add(value);
            }
            String excerpt = String.join("\n\n", parts);
            if (!excerpt.isBlank()) excerpts.add(excerpt);
            List<UUID> rawIds = new ArrayList<>();
            rawIds.add(row.id());
            for (WorkRecordRow item : raw) {
                boolean includes = item.source() == WorkRecordSource.FOCUS_SESSION
                        && (item.id().equals(row.id()) || (row.source() == WorkRecordSource.TASK_COMPLETION
                            && row.todoId() != null && row.todoId().equals(item.todoId())));
                if (includes) {
                    focus.putIfAbsent(item.id(), item.focusMs() == null ? 0L : item.focusMs());
                    if (!rawIds.contains(item.id())) rawIds.add(item.id());
                }
            }
            snapshots.add(new SourceSnapshot(row.id(), new ArrayList<>(entry.getValue()), excerpt, rawIds));
        }
        if (request.includeFocus()) {
            long ms = focus.values().stream().mapToLong(Long::longValue).sum();
            if (ms > 0) excerpts.add("所选素材专注投入：" + formatDuration(ms));
        }
        Input input = normalize(CommunityPostType.DAILY, request.date(), request.date() + " 今日分享", "",
                String.join("\n\n", excerpts));
        return create(owner, input, serialize(snapshots));
    }

    private OwnerPost create(UUID owner, Input input, String sourceSelection) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        validateAttachmentSelection(input.bodyMarkdown(), List.of());
        posts.insertPost(new CommunityRows.Post(id, owner, input.type(), CommunityPostStatus.DRAFT, 0, null, null, now, now));
        posts.insertDraft(new CommunityRows.Draft(id, owner, input.type(), input.businessDate(), input.title(), input.summary(),
                input.bodyMarkdown(), sourceSelection, now));
        events.publishAfterCommit(owner, "POST", id, "DRAFT");
        return get(id);
    }

    private CommunityRows.Post requireOwned(UUID owner, UUID postId, boolean lock) {
        return (lock ? posts.lockOwned(owner, postId) : posts.findOwned(owner, postId)).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "稿件不存在"));
    }

    private CommunityRows.Draft requireDraft(UUID owner, UUID postId) {
        return posts.findDraft(owner, postId).orElseThrow();
    }

    private void writeDraft(CommunityRows.Post post, Input input, String sources, Instant savedAt) {
        if (posts.updateDraft(new CommunityRows.Draft(post.id(), post.ownerId(), input.type(), input.businessDate(),
                input.title(), input.summary(), input.bodyMarkdown(), sources, savedAt)) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "稿件已变化，请刷新后重试");
        }
    }

    private void updateAggregate(CommunityRows.Post post, CommunityPostStatus status, UUID revision,
            Instant firstPublishedAt, Instant updatedAt) {
        if (posts.updateAggregate(post.ownerId(), post.id(), post.version(), status, revision, firstPublishedAt, updatedAt) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "稿件已被其他操作修改，请刷新后重试");
        }
    }

    private void requireVersion(CommunityRows.Post post, Long version) {
        if (version == null || version < 0) throw badRequest("稿件版本无效");
        if (post.version() != version) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "稿件已被其他操作修改，请刷新后重试");
        }
    }

    private void requireType(CommunityRows.Post post, CommunityPostType type) {
        if (post.type() != type) throw badRequest("已有稿件的类型不能更改，请新建另一类型稿件");
    }

    private Input normalize(CommunityPostType type, LocalDate date, String title, String summary, String body) {
        if (type == null) throw badRequest("请选择发布类型");
        if ((type == CommunityPostType.DAILY && date == null) || (type != CommunityPostType.DAILY && date != null)) {
            throw badRequest("业务日期与发布类型不符");
        }
        title = text(title); summary = text(summary); body = text(body);
        if (title.length() > 200 || summary.length() > 500 || body.length() > 100000) throw badRequest("稿件文字超过长度限制");
        return new Input(type, date, title, summary, body);
    }

    private List<UUID> attachmentIds(List<UUID> ids) {
        if (ids == null) return List.of();
        if (ids.stream().anyMatch(Objects::isNull)) throw badRequest("附件标识无效");
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private void validateAttachmentSelection(String body, List<UUID> ids) {
        AttachmentPersistenceService.validateBody(body, new HashSet<>(ids));
    }

    private Published published(CommunityRows.Revision revision, Instant first) {
        return new Published(revision.postId(), revision.resultVersion(), revision.id(), revision.revisionNo(), revision.publishedAt(), first);
    }

    private String fingerprint(PublishPost request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(serialize(request).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException impossible) { throw new IllegalStateException("无法序列化稿件", impossible); }
    }

    private static String text(String value) { return value == null ? "" : value; }
    private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        return seconds / 60 + " 分 " + seconds % 60 + " 秒";
    }
    private record Input(CommunityPostType type, LocalDate businessDate, String title, String summary, String bodyMarkdown) {}
    private record SourceSnapshot(UUID recordId, List<MaterialField> fields, String publicExcerpt, List<UUID> rawRecordIds) {}
}
