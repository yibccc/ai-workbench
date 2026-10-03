package com.aiworkbench.resume;

import com.aiworkbench.dto.resume.ResumeModels.*;
import com.aiworkbench.exception.ResumeException;
import com.aiworkbench.service.ResumeService;
import com.aiworkbench.service.impl.ResumePersistenceService;
import com.aiworkbench.service.impl.ResumeServiceImpl;
import com.aiworkbench.storage.*;
import com.aiworkbench.support.OwnerTestContext;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real PostgreSQL transactions and original RustFS bytes; spies only inject synchronization/failure. */
@SpringBootTest
class ResumePersistenceIntegrationTest {
    @Autowired ResumeService service;
    @Autowired ResumePersistenceService persistence;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean ObjectStorage storage;
    UUID owner,other;
    @TempDir Path scratch;
    @BeforeEach void setup() { owner=account(); other=account(); OwnerTestContext.use(owner); }
    UUID account() {
        UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,?,?,'USER')",id,"resume-"+id,"synthetic hash"); return id;
    }
    @AfterEach void cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS resume_reject_ready ON resume_objects");
        jdbc.execute("DROP FUNCTION IF EXISTS resume_reject_ready()");
        jdbc.execute("DROP TRIGGER IF EXISTS resume_slow_current ON user_resumes");
        jdbc.execute("DROP FUNCTION IF EXISTS resume_slow_current()");
        reset(storage);
        for(UUID id:List.of(owner,other)) {
            for(String key:jdbc.queryForList("SELECT storage_key FROM resume_objects WHERE user_id=?",String.class,id)) storage.delete(key);
            jdbc.update("DELETE FROM resume_write_receipts WHERE user_id=?",id);
            jdbc.update("DELETE FROM user_resumes WHERE user_id=?",id);
            jdbc.update("DELETE FROM resume_objects WHERE user_id=?",id);
            jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
        }
        SecurityContextHolder.clearContext();
    }
    Receipt save(long version,String text) { return service.save(new Save(Mode.PASTE,text,version,UUID.randomUUID())); }
    Receipt upload(long version,UUID request,String original,String text) { return service.importMarkdown(version,request,text,"原件.md",new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8))); }
    @Test void pureReadEmptyBodyReceiptReplayDeleteAndSnapshotAreOwnerScopedAndMonotonic() {
        assertThat(service.current()).isEqualTo(new Current(false,0,null,null,null));
        Snapshot absent=new TransactionTemplate(transactions).execute(tx->persistence.snapshotForInterview(owner,0));
        assertThat(absent).isEqualTo(new Snapshot(false,0,null,null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_resumes WHERE user_id=?",Integer.class,owner)).isZero();
        var request=new Save(Mode.PASTE,"",0L,UUID.randomUUID()); var first=service.save(request);
        assertThat(service.current().exists()).isTrue(); assertThat(service.current().markdownText()).isEmpty();
        assertThat(service.save(request)).isEqualTo(first);
        assertThatThrownBy(()->service.save(new Save(Mode.PASTE,"different",0L,request.requestId())))
                .isInstanceOfSatisfying(ResumeException.class,e->assertThat(e.code()).isEqualTo("RESUME_REQUEST_CONFLICT"));
        var snapshot=new TransactionTemplate(transactions).execute(tx->persistence.snapshotForInterview(owner,1));
        assertThat(snapshot).isEqualTo(new Snapshot(true,1,"",ResumeServiceImpl.hash("")));
        service.delete(1,UUID.randomUUID()); assertThat(service.current()).isEqualTo(new Current(false,2,null,null,null));
        save(2,"new"); assertThat(service.current().version()).isEqualTo(3);
        assertThat(snapshot.markdownText()).isEmpty();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(tx->persistence.snapshotForInterview(owner,1)))
                .isInstanceOf(ResumeException.class);
        OwnerTestContext.use(other); assertThat(service.current()).isEqualTo(new Current(false,0,null,null,null));
        assertThatThrownBy(service::original).isInstanceOf(ResumeException.class);
        assertThatThrownBy(()->service.save(new Save(Mode.EDIT_CURRENT,"x",0L,UUID.randomUUID()))).isInstanceOf(ResumeException.class);
        assertThat(jdbc.queryForObject("SELECT version FROM user_resumes WHERE user_id=?",Long.class,owner)).isEqualTo(3);
    }
    @Test void importKeepsExactBytesIndependentFinalTextEditPreservesAndPasteDetachesWithoutPut() throws Exception {
        UUID request=UUID.randomUUID(); String original="\uFEFF# 原始\r\ntext",edited="# edited <script>inert</script>";
        var first=upload(0,request,original,edited); var current=service.current();
        assertThat(current.markdownText()).isEqualTo(edited); assertThat(current.sourceKind()).isEqualTo("MD_FILE");
        assertThat(current.originalFile().sha256()).isEqualTo(ResumeServiceImpl.hash(original));
        try(var object=service.original().object()) { assertThat(object.stream().readAllBytes()).isEqualTo(original.getBytes(StandardCharsets.UTF_8)); }
        assertThat(upload(0,request,original,edited)).isEqualTo(first);
        verify(storage,times(1)).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
        service.save(new Save(Mode.EDIT_CURRENT,"edited again",1L,UUID.randomUUID()));
        assertThat(service.current().originalFile()).isEqualTo(current.originalFile());
        assertThat(service.cleanup().results()).isEmpty();
        save(2,"pasted"); assertThat(service.current().sourceKind()).isEqualTo("PASTE"); assertThat(service.current().originalFile()).isNull();
        assertThatThrownBy(service::original).isInstanceOf(ResumeException.class);
        assertThat(service.cleanup().results()).extracting(Result::state).containsExactly("DELETED");
        assertThat(upload(0,request,original,edited)).isEqualTo(first);
        assertThat(service.current().markdownText()).isEqualTo("pasted");
        assertThat(jdbc.queryForObject("SELECT original_filename FROM resume_objects WHERE id=?",String.class,first.objectId())).isNull();
        verify(storage,times(1)).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
    }
    @Test void bothIndependentTextLimitsAndFormatFailuresLeaveCurrentAndVersionUnchanged() {
        save(0,"preserved");
        for(String unit:List.of("字","😀")) {
            long version=service.current().version(); upload(version,UUID.randomUUID(),unit.repeat(20000),unit.repeat(20000));
            long stable=service.current().version(); String body=service.current().markdownText();
            assertThatThrownBy(()->upload(stable,UUID.randomUUID(),unit.repeat(20001),"valid")).isInstanceOf(ResumeException.class);
            assertThatThrownBy(()->upload(stable,UUID.randomUUID(),"valid",unit.repeat(20001))).isInstanceOf(ResumeException.class);
            assertThat(service.current().version()).isEqualTo(stable); assertThat(service.current().markdownText()).isEqualTo(body);
        }
        long version=service.current().version();
        assertThatThrownBy(()->service.importMarkdown(version,UUID.randomUUID(),"safe","wrong.pdf",new ByteArrayInputStream(new byte[0]))).isInstanceOf(ResumeException.class);
        assertThatThrownBy(()->service.importMarkdown(version,UUID.randomUUID(),"safe","bad.md",new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28}))).isInstanceOf(com.aiworkbench.exception.AttachmentException.class);
        byte[] exact=new byte[1048576]; Arrays.fill(exact,(byte)'a');
        assertThatThrownBy(()->service.importMarkdown(version,UUID.randomUUID(),"safe","exact.md",new ByteArrayInputStream(exact))).isInstanceOf(ResumeException.class);
        assertThatThrownBy(()->service.importMarkdown(version,UUID.randomUUID(),"safe","over.md",new ByteArrayInputStream(Arrays.copyOf(exact,exact.length+1))))
                .isInstanceOfSatisfying(com.aiworkbench.exception.AttachmentException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(413));
        assertThat(service.current().version()).isEqualTo(version);
        upload(version,UUID.randomUUID(),"",""); assertThat(service.current().exists()).isTrue();
    }
    @Test void databaseCompositeForeignKeyRejectsOtherOwnersOriginalWithoutPartialCurrentChange() {
        var original=upload(0,UUID.randomUUID(),"owner A original","owner A body");
        OwnerTestContext.use(other); save(0,"owner B body");
        assertThatThrownBy(()->jdbc.update("UPDATE user_resumes SET source_kind='MD_FILE',current_object_id=? WHERE user_id=?",original.objectId(),other))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.current().sourceKind()).isEqualTo("PASTE"); assertThat(service.current().markdownText()).isEqualTo("owner B body");
        assertThat(jdbc.queryForObject("SELECT markdown_text FROM user_resumes WHERE user_id=?",String.class,owner)).isEqualTo("owner A body");
    }
    @Test void twoIndependentTransactionsWithSameExpectedVersionHaveExactlyOneWinner() throws Exception {
        var barrier=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(String body:List.of("one","two")) results.add(pool.submit(()->{
                OwnerTestContext.use(owner); barrier.await();
                try { save(0,body); return true; } catch(ResumeException conflict) { assertThat(conflict.code()).isEqualTo("VERSION_CONFLICT"); return false; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            assertThat(List.of(results.get(0).get(10,TimeUnit.SECONDS),results.get(1).get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT version FROM user_resumes WHERE user_id=?",Long.class,owner)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM resume_write_receipts WHERE user_id=?",Integer.class,owner)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void twoConcurrentImportsCanReserveButOnlyOneCasSwapsCurrent() throws Exception {
        var both=new CountDownLatch(2); var release=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        doAnswer(call->{ both.countDown(); assertThat(release.await(20,TimeUnit.SECONDS)).isTrue(); return call.callRealMethod(); })
                .when(storage).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(String body:List.of("one","two")) results.add(pool.submit(OwnerTestContext.as(owner,()->{
                try { upload(0,UUID.randomUUID(),body,body); return true; }
                catch(ResumeException conflict) { assertThat(conflict.getStatusCode().value()).isEqualTo(409); return false; }
            })));
            assertThat(both.await(15,TimeUnit.SECONDS)).isTrue(); release.countDown();
            assertThat(List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT version FROM user_resumes WHERE user_id=?",Long.class,owner)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM resume_objects WHERE user_id=? AND status='READY'",Integer.class,owner)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM resume_write_receipts WHERE user_id=? AND state='SUCCEEDED'",Integer.class,owner)).isEqualTo(1);
            assertThat(service.cleanup().results()).extracting(Result::state).containsExactly("DELETED");
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void putAndDatabaseConfirmationFailuresRetainOldCurrentDurableKeyAndImmutableFailedReceipt() {
        save(0,"old"); UUID request=UUID.randomUUID();
        doThrow(new StorageException(new IOException())).when(storage).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
        assertThatThrownBy(()->upload(1,request,"bytes","new")).isInstanceOfSatisfying(ResumeException.class,e->assertThat(e.code()).isEqualTo("STORAGE_UNAVAILABLE"));
        reset(storage); assertThat(service.current().markdownText()).isEqualTo("old"); assertThat(service.current().version()).isEqualTo(1);
        assertThat(service.cleanup().results()).isEmpty();
        jdbc.update("UPDATE resume_objects SET cleanup_after=? WHERE user_id=? AND request_id=?",Timestamp.from(Instant.now().minusSeconds(600)),owner,request);
        assertThat(service.cleanup().results()).extracting(Result::state).containsExactly("DELETED");
        assertThatThrownBy(()->upload(1,request,"bytes","new")).isInstanceOfSatisfying(ResumeException.class,e->assertThat(e.code()).isEqualTo("STORAGE_UNAVAILABLE"));
        jdbc.execute("CREATE FUNCTION resume_reject_ready() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='READY' THEN RAISE EXCEPTION 'synthetic reject'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER resume_reject_ready BEFORE UPDATE ON resume_objects FOR EACH ROW EXECUTE FUNCTION resume_reject_ready()");
        assertThatThrownBy(()->upload(1,UUID.randomUUID(),"bytes","new"))
                .isInstanceOfSatisfying(ResumeException.class,e->assertThat(e.code()).isEqualTo("UPLOAD_CONFIRMATION_FAILED"));
        assertThat(service.current().markdownText()).isEqualTo("old");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resume_objects WHERE user_id=? AND status='FAILED'",Integer.class,owner)).isEqualTo(1);
    }
    @Test void leaseExpiresDuringCurrentWriteLastDatabaseFenceRollsBackAndRetainsCleanupReceipt() {
        save(0,"preserved"); UUID request=UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION resume_slow_current() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.current_object_id IS NOT NULL THEN UPDATE resume_objects SET lease_expires_at=clock_timestamp()+interval '150 milliseconds' WHERE user_id=NEW.user_id AND id=NEW.current_object_id; PERFORM pg_sleep(0.3); END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER resume_slow_current BEFORE UPDATE ON user_resumes FOR EACH ROW EXECUTE FUNCTION resume_slow_current()");
        assertThatThrownBy(()->upload(1,request,"candidate bytes","candidate current"))
                .isInstanceOfSatisfying(ResumeException.class,e->assertThat(e.code()).isEqualTo("UPLOAD_EXPIRED"));
        assertThat(service.current()).isEqualTo(new Current(true,1,"preserved","PASTE",null));
        assertThat(jdbc.queryForObject("SELECT status FROM resume_objects WHERE user_id=? AND request_id=?",String.class,owner,request)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resume_objects WHERE user_id=? AND status='READY'",Integer.class,owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM resume_write_receipts WHERE user_id=? AND request_id=?",String.class,owner,request)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT result_version FROM resume_write_receipts WHERE user_id=? AND request_id=?",Long.class,owner,request)).isEqualTo(1);
        assertThat(service.cleanup().results()).extracting(Result::state).containsExactly("DELETED");
        assertThatThrownBy(()->upload(1,request,"candidate bytes","candidate current"))
                .isInstanceOfSatisfying(ResumeException.class,e->assertThat(e.code()).isEqualTo("UPLOAD_EXPIRED"));
        verify(storage,times(1)).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
    }
    @Test void activeLatePutExpiredTokenCannotResurrectAndDeleteFailureIsRetryable() throws Exception {
        save(0,"old"); var started=new CountDownLatch(1); var release=new CountDownLatch(1); UUID request=UUID.randomUUID();
        doAnswer(call->{ assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); started.countDown(); assertThat(release.await(20,TimeUnit.SECONDS)).isTrue(); return call.callRealMethod(); })
                .when(storage).put(anyString(),any(Path.class),anyLong(),anyString(),anyString());
        var pool=Executors.newSingleThreadExecutor();
        try {
            Future<?> pending=pool.submit(OwnerTestContext.as(owner,()->upload(1,request,"late bytes","late current")));
            assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
            UUID id=jdbc.queryForObject("SELECT id FROM resume_objects WHERE user_id=? AND request_id=?",UUID.class,owner,request);
            assertThat(upload(1,request,"late bytes","late current").state()).isEqualTo("UPLOADING");
            jdbc.update("UPDATE resume_objects SET lease_expires_at=?,cleanup_after=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(600)),Timestamp.from(Instant.now().minusSeconds(600)),id);
            assertThat(service.recover().results()).extracting(Result::state).containsExactly("FAILED");
            assertThat(service.cleanup().results()).isEmpty(); // Known active I/O must not be physically deleted.
            service.delete(1,UUID.randomUUID()); save(2,"new stable");
            release.countDown(); assertThatThrownBy(()->pending.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            assertThat(service.current().markdownText()).isEqualTo("new stable"); assertThat(service.current().version()).isEqualTo(3);
            String key="interview/resumes/"+owner+"/"+id+".md";
            doThrow(new StorageException(new IOException())).when(storage).delete(key);
            assertThat(service.cleanup().results()).extracting(Result::state).containsExactly("DELETE_FAILED");
            reset(storage); assertThat(service.cleanup().results()).extracting(Result::state).containsExactly("DELETED");
            assertThatThrownBy(()->storage.open(key)).isInstanceOf(StorageException.class);
            assertThat(jdbc.queryForObject("SELECT state FROM resume_write_receipts WHERE user_id=? AND request_id=?",String.class,owner,request)).isEqualTo("FAILED");
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void unknownCrashTombstoneRepeatsDeleteAndOldTokenDoesNotBind() throws Exception {
        save(0,"baseline"); UUID request=UUID.randomUUID();
        try(var file=new AttachmentValidator().stage("crash.md",new ByteArrayInputStream("crash".getBytes(StandardCharsets.UTF_8)))) {
            var reservation=persistence.reserve(owner,1,request,"b".repeat(64),file); var row=reservation.object();
            jdbc.update("UPDATE resume_objects SET lease_expires_at=?,cleanup_after=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(600)),Timestamp.from(Instant.now().minusSeconds(600)),row.id());
            service.recover(); assertThat(service.cleanup().results()).extracting(Result::state).containsExactly("DELETED");
            storage.put(row.storageKey(),file.path(),file.size(),file.contentType(),file.sha256()); // Unknown result arriving after a prior delete.
            assertThat(persistence.finish(owner,row.id(),row.uploadToken(),1,"late",ResumeServiceImpl.hash("late")).state()).isEqualTo("FAILED");
            assertThat(service.cleanup().results()).extracting(Result::state).containsExactly("DELETED");
            assertThatThrownBy(()->storage.open(row.storageKey())).isInstanceOf(StorageException.class);
            assertThat(service.current().markdownText()).isEqualTo("baseline");
        }
    }
    @Test void lateIoAfterProviderDeleteButBeforeDeleteConfirmationRequeuesTombstone() throws Exception {
        save(0,"stable");
        try(var file=new AttachmentValidator().stage("late.md",new ByteArrayInputStream("late bytes".getBytes(StandardCharsets.UTF_8)))) {
            var row=persistence.reserve(owner,1,UUID.randomUUID(),"d".repeat(64),file).object();
            assertThat(persistence.finish(owner,row.id(),UUID.randomUUID(),1,"wrong token",ResumeServiceImpl.hash("wrong token")).state()).isEqualTo("UPLOADING");
            assertThat(jdbc.queryForObject("SELECT status FROM resume_objects WHERE id=?",String.class,row.id())).isEqualTo("UPLOADING");
            jdbc.update("UPDATE resume_objects SET lease_expires_at=?,cleanup_after=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(600)),Timestamp.from(Instant.now().minusSeconds(600)),row.id());
            service.recover(); var claim=persistence.claimCleanup(owner,Set.of()).get(0);
            storage.delete(row.storageKey());
            storage.put(row.storageKey(),file.path(),file.size(),file.contentType(),file.sha256());
            persistence.completeIo(owner,row.id(),row.uploadToken());
            assertThat(persistence.finishCleanup(owner,row.id(),claim.operationToken(),true).state()).isEqualTo("DELETE_FAILED");
            assertThat(service.cleanup().results()).extracting(Result::state).containsExactly("DELETED");
            assertThatThrownBy(()->storage.open(row.storageKey())).isInstanceOf(StorageException.class);
            assertThat(service.current().markdownText()).isEqualTo("stable");
        }
    }
    @Test void forciblyKilledUploadingJvmRestartsAndRecoversOriginalKeyWithoutChangingCurrent() throws Exception {
        save(0,"before crash"); Path committed=scratch.resolve("committed.txt");
        Process process=harness("reserve",committed);
        UUID id;
        try {
            awaitMarker(process,committed);
            id=UUID.fromString(Files.readString(committed,StandardCharsets.UTF_8));
            assertThat(jdbc.queryForObject("SELECT status FROM resume_objects WHERE user_id=? AND id=?",String.class,owner,id)).isEqualTo("UPLOADING");
            assertThat(service.current().markdownText()).isEqualTo("before crash");
        } finally {
            process.destroyForcibly(); assertThat(process.waitFor(15,TimeUnit.SECONDS)).isTrue();
        }
        jdbc.update("UPDATE resume_objects SET lease_expires_at=?,cleanup_after=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(600)),Timestamp.from(Instant.now().minusSeconds(600)),id);
        Path recovered=scratch.resolve("recovered.txt"); Process restarted=harness("recover",recovered);
        try {
            awaitMarker(restarted,recovered); assertThat(restarted.waitFor(20,TimeUnit.SECONDS)).isTrue(); assertThat(restarted.exitValue()).isZero();
        } finally { if(restarted.isAlive()) { restarted.destroyForcibly(); restarted.waitFor(15,TimeUnit.SECONDS); } }
        assertThat(jdbc.queryForObject("SELECT status FROM resume_objects WHERE user_id=? AND id=?",String.class,owner,id)).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT state FROM resume_write_receipts WHERE user_id=? AND object_id=?",String.class,owner,id)).isEqualTo("FAILED");
        assertThat(service.current().markdownText()).isEqualTo("before crash"); assertThat(service.current().version()).isEqualTo(1);
        String key="interview/resumes/"+owner+"/"+id+".md"; assertThatThrownBy(()->storage.open(key)).isInstanceOf(StorageException.class);
    }
    Process harness(String operation,Path marker) throws IOException {
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var builder=new ProcessBuilder(executable,"-Xms32m","-Xmx192m","-XX:MaxMetaspaceSize=160m","-XX:ActiveProcessorCount=2",
                "-XX:+UseSerialGC","-XX:TieredStopAtLevel=1","-XX:ReservedCodeCacheSize=48m","-cp",classpath,
                ResumeCrashHarness.class.getName(),operation,owner.toString(),marker.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().keySet().removeIf(key->key.matches("(?i)^(RUSTFS_|AWS_|DEEPSEEK_).*$"));
        builder.environment().put("DEEPSEEK_API_KEY","");
        return builder.start();
    }
    void awaitMarker(Process process,Path marker) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        while(!Files.exists(marker) && process.isAlive() && System.nanoTime()<deadline) Thread.sleep(100);
        assertThat(Files.exists(marker)).as("real child JVM reached its committed synchronization point").isTrue();
    }
}
