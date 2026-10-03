package com.aiworkbench.community;

import com.aiworkbench.dto.community.CommunityModels.UpdateProfile;
import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.enums.CommunityPostStatus;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.service.*;
import com.aiworkbench.support.OwnerTestContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class CommunityPublishingIntegrationTest {
    @Autowired PublishingService publishing;
    @Autowired CommunityService community;
    @Autowired CommunityProfileService profiles;
    @Autowired CommunityModerationService moderation;
    @Autowired JdbcTemplate jdbc;
    UUID owner;
    UUID admin;

    @BeforeEach void setup() {
        owner = account("USER"); admin = account("ADMIN");
        OwnerTestContext.use(owner);
    }
    @AfterEach void cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_community_revision ON community_post_revisions");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_community_revision()");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_community_hide ON community_posts");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_community_hide()");
        CommunityTestData.remove(jdbc, owner);
        CommunityTestData.remove(jdbc, admin);
        SecurityContextHolder.clearContext();
    }
    UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,?,?,?)", id, "community-"+id,"synthetic hash",role);
        return id;
    }
    PublishPost payload(OwnerPost post, UUID requestId, String body) {
        return new PublishPost(requestId, post.version(), "MEMBERS", post.type(), post.draft().businessDate(),
                post.draft().title(), post.draft().summary(), body, List.of());
    }

    @Test void privateSaveAndCurrentPreviewPublishFreezeAllPublicFieldsAndHistoricalResult() {
        var post = publishing.create(new CreatePost(CommunityPostType.DAILY, LocalDate.of(2055,1,1), "原标题", "原摘要", "旧草稿"));
        assertThat(community.page(null, owner, 0, 5).items()).isEmpty();
        UUID key = UUID.randomUUID();
        var firstRequest = payload(post, key, "预览正文，无需先保存");
        var first = publishing.publish(post.postId(), firstRequest);
        assertThat(publishing.get(post.postId()).draft().bodyMarkdown()).isEqualTo(firstRequest.bodyMarkdown());
        var old = community.get(post.postId());
        var saved = publishing.save(post.postId(), new SaveDraft(first.version(), post.type(), LocalDate.of(2055,1,2),
                "新标题", "新摘要", "新私有正文", List.of()));
        assertThat(community.get(post.postId())).isEqualTo(old);
        assertThat(publishing.publish(post.postId(), firstRequest)).isEqualTo(first);
        assertThat(publishing.get(post.postId()).version()).isEqualTo(saved.version());
        var current = publishing.get(post.postId());
        var second = publishing.publish(post.postId(), payload(current, UUID.randomUUID(), current.draft().bodyMarkdown()));
        var reader = community.get(post.postId());
        assertThat(reader.title()).isEqualTo("新标题");
        assertThat(reader.summary()).isEqualTo("新摘要");
        assertThat(reader.businessDate()).isEqualTo(LocalDate.of(2055,1,2));
        assertThat(reader.bodyMarkdown()).isEqualTo("新私有正文");
        assertThat(reader.firstPublishedAt()).isEqualTo(old.firstPublishedAt());
        assertThat(second.revisionNo()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT body_markdown FROM community_post_revisions WHERE id=?", String.class, first.revisionId()))
                .isEqualTo(firstRequest.bodyMarkdown());
        assertThat(jdbc.queryForObject("SELECT business_date FROM community_post_revisions WHERE id=?", LocalDate.class, first.revisionId()))
                .isEqualTo(LocalDate.of(2055,1,1));
        publishing.withdraw(post.postId(), new Version(second.version()));
        assertNotFound(() -> community.get(post.postId()));
        assertThat(publishing.publish(post.postId(), firstRequest)).isEqualTo(first);
        assertThat(publishing.get(post.postId()).status()).isEqualTo(CommunityPostStatus.WITHDRAWN);
        assertConflict(() -> publishing.publish(post.postId(), new PublishPost(key, 0L, "MEMBERS", post.type(), post.draft().businessDate(),
                "原标题", "原摘要", "异载", List.of())));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_post_revisions WHERE post_id=?", Integer.class, post.postId())).isEqualTo(2);
    }

    @Test void allTypesHaveImmutableTypeMemberScopeAndOwnerBoundary() {
        for (var type : CommunityPostType.values()) {
            var post = publishing.create(new CreatePost(type, type == CommunityPostType.DAILY ? LocalDate.of(2055,2,1) : null,
                    type == CommunityPostType.MOMENT ? "" : "标题", "摘要", "正文"));
            assertNotFound(() -> community.get(post.postId()));
            assertBadRequest(() -> publishing.publish(post.postId(), new PublishPost(UUID.randomUUID(), 0L, "ANONYMOUS", type,
                    post.draft().businessDate(), post.draft().title(), "", "正文", List.of())));
            assertThat(publishing.get(post.postId()).version()).isZero();
            publishing.publish(post.postId(), payload(post, UUID.randomUUID(), "正文"));
            assertThat(community.get(post.postId()).type()).isEqualTo(type);
            OwnerTestContext.use(admin);
            assertNotFound(() -> publishing.get(post.postId()));
            assertNotFound(() -> publishing.save(post.postId(), new SaveDraft(999L, type, post.draft().businessDate(), "", "", "", List.of())));
            OwnerTestContext.use(owner);
            assertBadRequest(() -> publishing.save(post.postId(), new SaveDraft(1L,
                    type == CommunityPostType.BLOG ? CommunityPostType.MOMENT : CommunityPostType.BLOG, null, "", "", "", List.of())));
        }
        assertThat(community.page(null, owner, 0, 5).items()).hasSize(3);
        assertThat(community.author(owner).nickname()).isEqualTo("未设置昵称");
    }

    @Test void hiddenContentRetainsPrivateDraftButCannotBeRestoredByNewOrOldPublish() {
        var post = publishing.create(new CreatePost(CommunityPostType.BLOG, null, "下架测试", "摘要", "正文"));
        var request = payload(post, UUID.randomUUID(), "正文");
        var result = publishing.publish(post.postId(), request);
        // A private save must not make the publicly visible revision token stale for moderation.
        publishing.save(post.postId(), new SaveDraft(1L, post.type(), null, "修改私有标题", "", "私有正文", List.of()));
        OwnerTestContext.use(admin);
        // Use the persisted ADMIN role for direct-service tests.
        SecurityContextHolder.getContext().setAuthentication(com.aiworkbench.security.WorkbenchAuthentications.authentication(
                new com.aiworkbench.security.WorkbenchPrincipal(admin,"ADMIN",0)));
        moderation.hide(post.postId(), new HidePost(result.revisionId(), "违反本站规则"));
        assertNotFound(() -> community.get(post.postId()));
        assertThat(community.page(null, owner, 0, 5).items()).isEmpty();
        assertNotFound(() -> moderation.hide(post.postId(), new HidePost(result.revisionId(), "重试")));
        OwnerTestContext.use(owner);
        var hidden = publishing.get(post.postId());
        assertThat(hidden.moderation().reason()).isEqualTo("违反本站规则");
        assertThat(publishing.publish(post.postId(), request)).isEqualTo(result);
        assertConflict(() -> publishing.publish(post.postId(), payload(hidden, UUID.randomUUID(), "重发")));
        assertConflict(() -> publishing.withdraw(post.postId(), new Version(hidden.version())));
        publishing.save(post.postId(), new SaveDraft(hidden.version(), post.type(), null,"私有可修改", "", "私有正文",List.of()));
        assertThat(publishing.get(post.postId()).status()).isEqualTo(CommunityPostStatus.HIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_moderation_audit WHERE post_id=?", Integer.class, post.postId())).isEqualTo(1);
    }

    @Test void publishFailureRollsBackDraftRevisionPointerAndVersion() {
        var post = publishing.create(new CreatePost(CommunityPostType.BLOG, null, "事务", "旧摘要", "原草稿"));
        jdbc.execute("CREATE FUNCTION reject_community_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected revision failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_community_revision BEFORE INSERT ON community_post_revisions FOR EACH ROW EXECUTE FUNCTION reject_community_revision()");
        assertThatThrownBy(() -> publishing.publish(post.postId(), payload(post, UUID.randomUUID(), "本次输入")))
                .hasMessageContaining("injected revision failure");
        assertThat(publishing.get(post.postId())).isEqualTo(post);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_post_revisions WHERE post_id=?", Integer.class,post.postId())).isZero();
        assertNotFound(() -> community.get(post.postId()));
    }

    @Test void hideFailureRollsBackAuditAndKeepsCurrentPublishedContent() {
        var post = publishing.create(new CreatePost(CommunityPostType.BLOG, null, "下架事务", "摘要", "正文"));
        var published = publishing.publish(post.postId(), payload(post, UUID.randomUUID(), "当前公开正文"));
        var ownerBefore = publishing.get(post.postId());
        var readerBefore = community.get(post.postId());
        jdbc.execute("CREATE FUNCTION reject_community_hide() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected hide failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_community_hide BEFORE UPDATE ON community_posts FOR EACH ROW WHEN (NEW.status = 'HIDDEN') EXECUTE FUNCTION reject_community_hide()");
        useAdmin();
        assertThatThrownBy(() -> moderation.hide(post.postId(), new HidePost(published.revisionId(), "有效下架理由")))
                .hasMessageContaining("injected hide failure");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_moderation_audit WHERE post_id=?", Integer.class, post.postId()))
                .isZero();
        assertThat(community.get(post.postId())).isEqualTo(readerBefore);
        OwnerTestContext.use(owner);
        assertThat(publishing.get(post.postId())).isEqualTo(ownerBefore);
    }

    @Test void concurrentPublishSameRequestReturnsOneReceiptAndDifferentWritesHaveOneWinner() throws Exception {
        var post = publishing.create(new CreatePost(CommunityPostType.MOMENT, null, "", "", ""));
        var request = payload(post, UUID.randomUUID(), "并发相同提交");
        var results = concurrent(() -> publishing.publish(post.postId(), request), () -> publishing.publish(post.postId(), request));
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_post_revisions WHERE post_id=?", Integer.class,post.postId())).isEqualTo(1);
        var current = publishing.get(post.postId());
        var different = concurrent(() -> publishing.publish(post.postId(), payload(current,UUID.randomUUID(),"写A")),
                () -> publishing.save(post.postId(),new SaveDraft(current.version(),post.type(),null,"","","写B",List.of())));
        assertThat(different.stream().filter(ResponseStatusException.class::isInstance)).hasSize(1);
        assertThat(publishing.get(post.postId()).version()).isEqualTo(2);
    }

    @Test void profilesHaveNoUsernameFallbackAndConcurrentInitialVersionHasOneWinner() throws Exception {
        assertThat(profiles.get().version()).isZero();
        assertThat(community.author(owner).nickname()).isEqualTo("未设置昵称");
        var results = concurrent(() -> profiles.update(new UpdateProfile("公开A", "简介A", 0L)),
                () -> profiles.update(new UpdateProfile("公开B", "简介B", 0L)));
        assertThat(results.stream().filter(ResponseStatusException.class::isInstance)).hasSize(1);
        var first = profiles.get();
        assertThat(first.version()).isEqualTo(1);
        assertConflict(() -> profiles.update(new UpdateProfile("旧写", "", 0L)));
        assertThat(profiles.update(new UpdateProfile("", "", first.version())).nickname()).isEqualTo("未设置昵称");
        assertThat(community.author(owner).bio()).isEmpty();
    }

    @Test void publishAndHideRaceNeverAllowsHiddenContentToReappear() throws Exception {
        for (int run=0;run<3;run++) {
            var post=publishing.create(new CreatePost(CommunityPostType.BLOG,null,"并发下架","","原正文"));
            var first=publishing.publish(post.postId(),payload(post,UUID.randomUUID(),"首次正文"));
            var current=publishing.get(post.postId());
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            ExecutorService pool=Executors.newFixedThreadPool(2);
            try {
                var update=pool.submit(OwnerTestContext.as(owner,guarded(
                        () -> publishing.publish(post.postId(),payload(current,UUID.randomUUID(),"竞争更新")),ready,go)));
                var hide=pool.submit(() -> {
                    try {
                        useAdmin();
                        return guarded(() -> moderation.hide(post.postId(),new HidePost(first.revisionId(),"并发下架理由")),ready,go).call();
                    } finally { SecurityContextHolder.clearContext(); }
                });
                assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
                Object updateResult=update.get(10,TimeUnit.SECONDS),hideResult=hide.get(10,TimeUnit.SECONDS);
                assertThat(updateResult instanceof ResponseStatusException ^ hideResult instanceof ResponseStatusException).isTrue();
                // If updating acquired the lock first, moderate the now-current public revision.
                if(hideResult instanceof ResponseStatusException) {
                    useAdmin();
                    moderation.hide(post.postId(),new HidePost(community.get(post.postId()).revisionId(),"下架当前版本"));
                }
                OwnerTestContext.use(owner);
                var hidden=publishing.get(post.postId());
                assertThat(hidden.status()).isEqualTo(CommunityPostStatus.HIDDEN);
                assertConflict(() -> publishing.publish(post.postId(),payload(hidden,UUID.randomUUID(),"不得恢复")));
                assertNotFound(() -> community.get(post.postId()));
                assertThat(jdbc.queryForObject("SELECT count(*) FROM community_moderation_audit WHERE post_id=?",Integer.class,post.postId())).isEqualTo(1);
            } finally { pool.shutdownNow();OwnerTestContext.use(owner); }
        }
    }

    void useAdmin() {
        SecurityContextHolder.getContext().setAuthentication(com.aiworkbench.security.WorkbenchAuthentications.authentication(
                new com.aiworkbench.security.WorkbenchPrincipal(admin,"ADMIN",0)));
    }

    List<Object> concurrent(Callable<?> a, Callable<?> b) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> first = guarded(a,ready,go), second = guarded(b,ready,go);
            var one = pool.submit(OwnerTestContext.as(owner, first));
            var two = pool.submit(OwnerTestContext.as(owner, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); go.countDown();
            return List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    Callable<Object> guarded(Callable<?> work, CountDownLatch ready, CountDownLatch go) {
        return () -> { ready.countDown(); go.await(5,TimeUnit.SECONDS);
            try { return work.call(); } catch (ResponseStatusException error) { return error; } };
    }
    static void assertNotFound(Runnable work) { assertStatus(work,404); }
    static void assertConflict(Runnable work) { assertStatus(work,409); }
    static void assertBadRequest(Runnable work) { assertStatus(work,400); }
    static void assertStatus(Runnable work,int status) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(status));
    }
}
