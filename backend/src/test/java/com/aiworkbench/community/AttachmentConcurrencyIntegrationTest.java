package com.aiworkbench.community;

import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.exception.AttachmentException;
import com.aiworkbench.service.*;
import com.aiworkbench.service.impl.AttachmentPersistenceService;
import com.aiworkbench.storage.*;
import com.aiworkbench.support.AttachmentTestFiles;
import com.aiworkbench.support.OwnerTestContext;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
class AttachmentConcurrencyIntegrationTest {
    @Autowired PublishingService publishing;
    @Autowired CommunityService community;
    @Autowired AttachmentService service;
    @Autowired AttachmentPersistenceService persistence;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean ObjectStorage storage;
    UUID owner,other;
    final List<String> createdKeys=new ArrayList<>();
    @BeforeEach void setup() { owner=account(); other=account(); OwnerTestContext.use(owner); }
    @AfterEach void cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS attachment_reject_ready ON community_attachments");
        jdbc.execute("DROP FUNCTION IF EXISTS attachment_reject_ready()");
        jdbc.execute("DROP TRIGGER IF EXISTS attachment_reject_revision ON community_post_revisions");
        jdbc.execute("DROP FUNCTION IF EXISTS attachment_reject_revision()");
        reset(storage);
        createdKeys.addAll(jdbc.queryForList("SELECT object_key FROM community_attachments WHERE owner_id IN (?,?)",String.class,owner,other));
        for(String key:new HashSet<>(createdKeys)) storage.delete(key);
        CommunityTestData.remove(jdbc,owner); CommunityTestData.remove(jdbc,other); SecurityContextHolder.clearContext();
    }
    UUID account() { UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,?,?,'USER')",id,"attachment-"+id,"synthetic hash"); return id; }
    OwnerPost post() { return publishing.create(new CreatePost(CommunityPostType.BLOG,null,"title","","preserved body")); }
    com.aiworkbench.dto.community.AttachmentModels.Upload upload(UUID post,long version,UUID request,String text) { return service.upload(post,version,request,"file.md",new ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    SaveDraft save(long version,List<UUID> ids) { return new SaveDraft(version,CommunityPostType.BLOG,null,"title","","preserved body",ids); }
    PublishPost publish(long version,List<UUID> ids) { return new PublishPost(UUID.randomUUID(),version,"MEMBERS",CommunityPostType.BLOG,null,"title","","preserved body",ids); }

    @Test void f1PublishedF2DraftThenExplicitUpdateRetainsHistoryAndUniqueIds() throws Exception {
        var post=post(); var f1=upload(post.postId(),0,UUID.randomUUID(),"F1 original");
        var first=publishing.publish(post.postId(),publish(f1.version(),List.of(f1.attachment().id(),f1.attachment().id())));
        var f2=upload(post.postId(),first.version(),UUID.randomUUID(),"F2 original");
        assertThat(community.get(post.postId()).attachments()).extracting(a->a.id()).containsExactly(f1.attachment().id());
        var saved=publishing.save(post.postId(),save(f2.version(),List.of(f2.attachment().id())));
        assertThat(publishing.page(null,null,0,5).items().get(0).hasUnpublishedChanges()).isTrue();
        assertThat(community.get(post.postId()).attachments()).extracting(a->a.id()).containsExactly(f1.attachment().id());
        var second=publishing.publish(post.postId(),publish(saved.version(),List.of(f2.attachment().id())));
        assertThat(community.get(post.postId()).attachments()).extracting(a->a.id()).containsExactly(f2.attachment().id());
        assertThat(second.firstPublishedAt()).isEqualTo(first.firstPublishedAt());
        assertThat(service.cleanup(post.postId()).results()).isEmpty();
        try(var old=service.download(post.postId(),f1.attachment().id(),true).object()) { assertThat(new String(old.stream().readAllBytes())).isEqualTo("F1 original"); }
        OwnerTestContext.use(other);
        assertThatThrownBy(()->service.download(post.postId(),f1.attachment().id(),false)).isInstanceOf(AttachmentException.class);
        try(var current=service.download(post.postId(),f2.attachment().id(),false).object()) { assertThat(new String(current.stream().readAllBytes())).isEqualTo("F2 original"); }
    }
    @Test void replayBeforeOldVersionAndConflictingContentDoNotReserveTwice() {
        var post=post(); UUID request=UUID.randomUUID(); var first=upload(post.postId(),0,request,"original");
        publishing.save(post.postId(),save(first.version(),List.of(first.attachment().id())));
        assertThat(upload(post.postId(),0,request,"original")).isEqualTo(first);
        assertThatThrownBy(()->upload(post.postId(),0,request,"different")).isInstanceOfSatisfying(AttachmentException.class,e->assertThat(e.code()).isEqualTo("UPLOAD_REQUEST_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_attachments WHERE post_id=?",Integer.class,post.postId())).isEqualTo(1);
    }
    @Test void failureReleasesQuotaPreservesOldKeyAndNewRequestCreatesNewIdentity() {
        var post=post(); UUID request=UUID.randomUUID();
        doThrow(new StorageException(new IOException("synthetic unavailable"))).when(storage).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
        assertThatThrownBy(()->upload(post.postId(),0,request,"failed")).isInstanceOfSatisfying(AttachmentException.class,e->{assertThat(e.code()).isEqualTo("STORAGE_UNAVAILABLE");assertThat(e.currentVersion()).isEqualTo(2);});
        assertThat(publishing.get(post.postId()).draft().attachmentIds()).isEmpty();
        String old=jdbc.queryForObject("SELECT object_key FROM community_attachments WHERE post_id=?",String.class,post.postId());
        reset(storage);
        assertThatThrownBy(()->upload(post.postId(),0,request,"failed")).isInstanceOf(AttachmentException.class);
        var retry=upload(post.postId(),2,UUID.randomUUID(),"failed");
        assertThat("community/attachments/"+retry.attachment().id()).isNotEqualTo(old);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_attachments WHERE post_id=?",Integer.class,post.postId())).isEqualTo(2);
        assertThat(publishing.get(post.postId()).draft().bodyMarkdown()).isEqualTo("preserved body");
        jdbc.update("UPDATE community_attachments SET reservation_expires_at=? WHERE post_id=? AND request_id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(600)),post.postId(),request);
        assertThat(service.cleanup(post.postId()).results()).extracting(r->r.state()).containsExactly("DELETED");
        assertThatThrownBy(()->upload(post.postId(),0,request,"failed")).isInstanceOfSatisfying(AttachmentException.class,e->{assertThat(e.code()).isEqualTo("STORAGE_UNAVAILABLE");assertThat(e.currentVersion()).isEqualTo(2);});
    }
    @Test void delayedPutAfterOtherTabRemoveCannotResurrectAndTombstoneDeletesLateBytes() throws Exception {
        var post=post(); var started=new CountDownLatch(1); var release=new CountDownLatch(1);
        doAnswer(call->{ assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); started.countDown(); assertThat(release.await(10,TimeUnit.SECONDS)).isTrue(); return call.callRealMethod(); })
                .when(storage).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
        var pool=Executors.newSingleThreadExecutor();
        try {
            Future<?> pending=pool.submit(()->{OwnerTestContext.use(owner); try { return upload(post.postId(),0,UUID.randomUUID(),"late bytes"); } finally { SecurityContextHolder.clearContext(); }});
            assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
            var before=publishing.get(post.postId()); UUID id=before.attachments().get(0).id();
            assertThatThrownBy(()->publishing.publish(post.postId(),publish(before.version(),List.of(id)))).isInstanceOfSatisfying(AttachmentException.class,e->assertThat(e.code()).isEqualTo("ATTACHMENT_NOT_READY"));
            publishing.save(post.postId(),save(before.version(),List.of()));
            assertThat(service.cleanup(post.postId()).results()).isEmpty();
            jdbc.update("UPDATE community_attachments SET reservation_expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(600)),id);
            assertThat(service.cleanup(post.postId()).results()).extracting(r->r.state()).containsExactly("DELETED");
            release.countDown(); assertThatThrownBy(()->pending.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            assertThat(publishing.get(post.postId()).draft().attachmentIds()).isEmpty();
            assertThat(jdbc.queryForObject("SELECT state FROM community_attachments WHERE id=?",String.class,id)).isEqualTo("DELETED");
            assertThat(service.cleanup(post.postId()).results()).extracting(r->r.state()).containsExactly("DELETED");
            assertThatThrownBy(()->storage.open("community/attachments/"+id)).isInstanceOf(StorageException.class);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void crashReservationExplicitRecoveryAndCleanupFailureRetryAreDurable() throws Exception {
        var post=post(); UUID id;
        try(var file=new AttachmentValidator().stage("crash.md",new ByteArrayInputStream("reserved".getBytes()))) {
            var reserved=persistence.reserve(owner,post.postId(),0,UUID.randomUUID(),file); id=reserved.attachment().id();
            storage.put(reserved.attachment().objectKey(),file.path(),file.size(),file.contentType(),file.sha256());
        }
        assertThat(service.recover(post.postId()).results()).isEmpty();
        jdbc.update("UPDATE community_attachments SET reservation_expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(600)),id);
        var recovered=service.recover(post.postId()); assertThat(recovered.version()).isEqualTo(2);
        assertThat(recovered.results()).extracting(r->r.safeFailureCode()).containsExactly("UPLOAD_EXPIRED");
        assertThat(publishing.get(post.postId()).draft().attachmentIds()).isEmpty();
        doThrow(new StorageException(new IOException())).when(storage).delete("community/attachments/"+id);
        assertThat(service.cleanup(post.postId()).results()).extracting(r->r.state()).containsExactly("DELETE_FAILED");
        reset(storage);
        assertThat(service.cleanup(post.postId()).results()).extracting(r->r.state()).containsExactly("DELETED");
    }
    @Test void tenAttachmentsAndConcurrentSameRequestCannotExceedCurrentQuota() throws Exception {
        var post=post(); long version=0;
        for(int i=0;i<9;i++) version=upload(post.postId(),version,UUID.randomUUID(),"file "+i).version();
        final long expected=version; UUID key=UUID.randomUUID(); var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(2);
        try {
            List<Future<com.aiworkbench.dto.community.AttachmentModels.Upload>> futures=new ArrayList<>();
            for(int i=0;i<2;i++) futures.add(pool.submit(()->{OwnerTestContext.use(owner); barrier.await(); try{return upload(post.postId(),expected,key,"tenth");}finally{SecurityContextHolder.clearContext();}}));
            var one=futures.get(0).get(15,TimeUnit.SECONDS); var two=futures.get(1).get(15,TimeUnit.SECONDS);
            assertThat(one.attachment().id()).isEqualTo(two.attachment().id());
            assertThat(publishing.get(post.postId()).draft().attachmentIds()).hasSize(10);
            assertThatThrownBy(()->upload(post.postId(),10,UUID.randomUUID(),"eleventh")).isInstanceOfSatisfying(AttachmentException.class,e->assertThat(e.code()).isEqualTo("ATTACHMENT_QUOTA"));
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentFiftyMiBReservationRejectsNewBytesAndReplaysPendingWithoutAnotherPut() throws Exception {
        var post=post(); long version=0; List<UUID> readyIds=new ArrayList<>();
        byte[] pdf=AttachmentTestFiles.pdfExact(20_971_520),png=AttachmentTestFiles.pngExact(5_242_880);
        for(int i=0;i<3;i++) {
            byte[] bytes=i<2?pdf:png;
            var ready=service.upload(post.postId(),version,UUID.randomUUID(),i<2?"existing.pdf":"existing.png",new ByteArrayInputStream(bytes));
            version=ready.version(); readyIds.add(ready.attachment().id());
            assertThat(ready.attachment().state()).isEqualTo("READY");
        }
        assertThat(jdbc.queryForObject("SELECT sum(actual_size) FROM community_attachments WHERE post_id=? AND state='READY'",Long.class,post.postId())).isEqualTo(47_185_920);
        final long expected=version; UUID request=UUID.randomUUID();
        var started=new CountDownLatch(1); var release=new CountDownLatch(1);
        clearInvocations(storage);
        doAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            started.countDown();
            assertThat(release.await(30,TimeUnit.SECONDS)).isTrue();
            return call.callRealMethod();
        }).when(storage).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
        var pool=Executors.newSingleThreadExecutor();
        try {
            Future<com.aiworkbench.dto.community.AttachmentModels.Upload> pending=pool.submit(()->{
                OwnerTestContext.use(owner);
                try { return service.upload(post.postId(),expected,request,"reserved.png",new ByteArrayInputStream(png)); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(started.await(15,TimeUnit.SECONDS)).isTrue();
            UUID id=jdbc.queryForObject("SELECT id FROM community_attachments WHERE post_id=? AND request_id=? AND state='UPLOADING'",UUID.class,post.postId(),request);
            var reserved=publishing.get(post.postId());
            List<UUID> expectedIds=new ArrayList<>(readyIds); expectedIds.add(id);
            assertThat(reserved.version()).isEqualTo(expected+1);
            assertThat(reserved.draft().attachmentIds()).containsExactlyElementsOf(expectedIds);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM community_attachments WHERE post_id=?",Integer.class,post.postId())).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT sum(actual_size) FROM community_attachments WHERE post_id=?",Long.class,post.postId())).isEqualTo(52_428_800);
            assertThatThrownBy(()->upload(post.postId(),reserved.version(),UUID.randomUUID(),"a"))
                    .isInstanceOfSatisfying(AttachmentException.class,e->{
                        assertThat(e.getStatusCode().value()).isEqualTo(409);
                        assertThat(e.code()).isEqualTo("ATTACHMENT_QUOTA");
                        assertThat(e.currentVersion()).isEqualTo(reserved.version());
                    });
            var replay=service.upload(post.postId(),expected,request,"reserved.png",new ByteArrayInputStream(png));
            assertThat(replay.attachment().id()).isEqualTo(id);
            assertThat(replay.attachment().state()).isEqualTo("UPLOADING");
            assertThat(replay.version()).isEqualTo(reserved.version());
            verify(storage,times(1)).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
            assertThat(publishing.get(post.postId())).isEqualTo(reserved);
            release.countDown();
            var finished=pending.get(30,TimeUnit.SECONDS);
            assertThat(finished.attachment().id()).isEqualTo(id);
            assertThat(finished.attachment().state()).isEqualTo("READY");
            assertThat(finished.version()).isEqualTo(reserved.version());
            verify(storage,times(1)).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
            var after=publishing.get(post.postId());
            assertThat(after.version()).isEqualTo(reserved.version());
            assertThat(after.draft().bodyMarkdown()).isEqualTo(post.draft().bodyMarkdown());
            assertThat(after.draft().savedAt()).isEqualTo(post.draft().savedAt());
            assertThat(after.draft().attachmentIds()).containsExactlyElementsOf(expectedIds);
            assertThat(after.attachments()).extracting(a->a.state()).containsOnly("READY");
            assertThat(after.currentPublished()).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM community_attachments WHERE post_id=?",Integer.class,post.postId())).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT count(DISTINCT id) FROM community_attachments WHERE post_id=? AND state='READY'",Integer.class,post.postId())).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT sum(actual_size) FROM community_attachments WHERE post_id=?",Long.class,post.postId())).isEqualTo(52_428_800);
            try(var object=service.download(post.postId(),id,true).object()) {
                var digest=java.security.MessageDigest.getInstance("SHA-256"); long size=0; byte[] buffer=new byte[16_384]; int count;
                while((count=object.stream().read(buffer))!=-1) { size+=count; digest.update(buffer,0,count); }
                assertThat(object.size()).isEqualTo(png.length); assertThat(size).isEqualTo(png.length);
                assertThat(digest.digest()).isEqualTo(java.security.MessageDigest.getInstance("SHA-256").digest(png));
            }
        } finally {
            release.countDown(); pool.shutdownNow();
            try { assertThat(pool.awaitTermination(30,TimeUnit.SECONDS)).isTrue(); }
            finally { reset(storage); }
        }
    }
    @Test void databaseConfirmationFailureReleasesReservationAndLeavesDurableOrphanKey() {
        var post=post();
        jdbc.execute("CREATE FUNCTION attachment_reject_ready() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.state='READY' THEN RAISE EXCEPTION 'synthetic confirmation failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER attachment_reject_ready BEFORE UPDATE ON community_attachments FOR EACH ROW EXECUTE FUNCTION attachment_reject_ready()");
        assertThatThrownBy(()->upload(post.postId(),0,UUID.randomUUID(),"put succeeded before failed DB confirmation"))
                .isInstanceOfSatisfying(AttachmentException.class,e->{assertThat(e.code()).isEqualTo("UPLOAD_CONFIRMATION_FAILED");assertThat(e.currentVersion()).isEqualTo(2);});
        assertThat(publishing.get(post.postId()).draft().attachmentIds()).isEmpty();
        assertThat(publishing.get(post.postId()).draft().bodyMarkdown()).isEqualTo("preserved body");
        assertThat(publishing.get(post.postId()).attachments()).extracting(a->a.state()).containsExactly("FAILED");
    }
    @Test void revisionSubwriteFailureRollsBackDraftTextRefsVersionAndPointerTogether() {
        var post=post(); var f1=upload(post.postId(),0,UUID.randomUUID(),"F1");
        var first=publishing.publish(post.postId(),publish(f1.version(),List.of(f1.attachment().id())));
        var f2=upload(post.postId(),first.version(),UUID.randomUUID(),"F2"); var before=publishing.get(post.postId());
        jdbc.execute("CREATE FUNCTION attachment_reject_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic revision failure'; END $$");
        jdbc.execute("CREATE TRIGGER attachment_reject_revision BEFORE INSERT ON community_post_revisions FOR EACH ROW EXECUTE FUNCTION attachment_reject_revision()");
        assertThatThrownBy(()->publishing.publish(post.postId(),publish(f2.version(),List.of(f2.attachment().id())))).isInstanceOf(RuntimeException.class);
        assertThat(publishing.get(post.postId())).isEqualTo(before);
        assertThat(community.get(post.postId()).revisionId()).isEqualTo(first.revisionId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_revision_attachments WHERE post_id=?",Integer.class,post.postId())).isEqualTo(1);
    }
    @Test void deletingStateFencesBindingAndAbandonedDeleteCanBeRetriedWithoutStaleCompletion() {
        var post=post(); var ready=upload(post.postId(),0,UUID.randomUUID(),"orphan");
        publishing.save(post.postId(),save(ready.version(),List.of()));
        var first=persistence.claimCleanup(owner,post.postId()).get(0);
        assertThatThrownBy(()->publishing.save(post.postId(),save(2,List.of(ready.attachment().id()))))
                .isInstanceOfSatisfying(AttachmentException.class,e->assertThat(e.code()).isEqualTo("ATTACHMENT_NOT_READY"));
        assertThat(persistence.claimCleanup(owner,post.postId())).isEmpty();
        jdbc.update("UPDATE community_attachments SET reservation_expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(600)),first.id());
        var second=persistence.claimCleanup(owner,post.postId()).get(0); assertThat(second.attemptToken()).isNotEqualTo(first.attemptToken());
        assertThat(persistence.finishCleanup(owner,post.postId(),first.id(),first.attemptToken(),true).state()).isEqualTo("DELETING");
        storage.delete(second.objectKey());
        assertThat(persistence.finishCleanup(owner,post.postId(),second.id(),second.attemptToken(),true).state()).isEqualTo("DELETED");
    }
    @Test void foreignAttachmentAndCrossPostSameOwnerCannotBindOrDelete() {
        var post=post(); var own=upload(post.postId(),0,UUID.randomUUID(),"own"); var another=post();
        assertThatThrownBy(()->publishing.save(another.postId(),save(0,List.of(own.attachment().id())))).isInstanceOfSatisfying(AttachmentException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
        OwnerTestContext.use(other);
        assertThatThrownBy(()->service.cleanup(post.postId())).isInstanceOfSatisfying(AttachmentException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(()->service.download(post.postId(),own.attachment().id(),true)).isInstanceOfSatisfying(AttachmentException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
    }
}
