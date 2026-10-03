package com.aiworkbench.service.impl;

import com.aiworkbench.dto.resume.ResumeModels.*;
import com.aiworkbench.exception.ResumeException;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.ResumeService;
import com.aiworkbench.storage.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Provider calls are outside persistence transactions; database tokens remain authoritative. */
@Service
public class ResumeServiceImpl implements ResumeService {
    private final ResumePersistenceService persistence;
    private final AttachmentValidator validator;
    private final ObjectStorage storage;
    private final Set<UUID> activeIo=ConcurrentHashMap.newKeySet();
    public ResumeServiceImpl(ResumePersistenceService persistence,AttachmentValidator validator,ObjectStorage storage) {
        this.persistence=persistence; this.validator=validator; this.storage=storage;
    }
    @Override public Current current() { return persistence.current(CurrentUser.requireId()); }
    @Override public Receipt save(Save request) {
        UUID owner=CurrentUser.requireId();
        if(request==null || request.mode()==null || request.expectedVersion()==null) throw invalid();
        parameters(request.expectedVersion(),request.requestId()); validateText(request.markdownText());
        String contentHash=hash(request.markdownText());
        return persistence.save(owner,request,payload("PUT",request.mode().name(),Long.toString(request.expectedVersion()),contentHash),contentHash);
    }
    @Override public Receipt delete(long expectedVersion,UUID request) {
        UUID owner=CurrentUser.requireId(); parameters(expectedVersion,request);
        return persistence.delete(owner,expectedVersion,request,payload("DELETE",Long.toString(expectedVersion)));
    }
    @Override public Receipt importMarkdown(long expectedVersion,UUID request,String text,String fileName,InputStream input) {
        UUID owner=CurrentUser.requireId(); parameters(expectedVersion,request); validateText(text);
        if(fileName==null || !fileName.toLowerCase(Locale.ROOT).endsWith(".md") || input==null) throw invalid();
        try(var file=validator.stage(fileName,input)) {
            validateText(MarkdownText.decode(file.path()));
            String contentHash=hash(text);
            String payload=payload("IMPORT","MD_FILE",Long.toString(expectedVersion),contentHash,file.sha256(),Long.toString(file.size()),file.fileName());
            var reservation=persistence.reserve(owner,expectedVersion,request,payload,file);
            if(!reservation.created()) return response(reservation.receipt());
            var row=reservation.object(); activeIo.add(row.id()); boolean putConfirmed=false;
            try {
                try { storage.put(row.storageKey(),file.path(),file.size(),file.contentType(),file.sha256()); }
                catch(RuntimeException storageFailure) {
                    Receipt failed;
                    try { failed=persistence.fail(owner,row.id(),row.uploadToken(),"STORAGE_UNAVAILABLE"); }
                    catch(RuntimeException databaseFailure) {
                        throw new ResumeException(HttpStatus.SERVICE_UNAVAILABLE,"UPLOAD_CONFIRMATION_FAILED","导入结果未能确认，请刷新并恢复未完成上传");
                    }
                    return response(failed);
                }
                putConfirmed=true;
                try { return response(persistence.finish(owner,row.id(),row.uploadToken(),expectedVersion,text,contentHash)); }
                catch(ResumeException expectedFailure) {
                    if(expectedFailure.code().equals("UPLOAD_EXPIRED")) {
                        // The last database fence rolled back its child writes. Record failure
                        // in a new transaction so the uploaded candidate is safely cleanable.
                        try { persistence.fail(owner,row.id(),row.uploadToken(),"UPLOAD_EXPIRED"); }
                        catch(RuntimeException unavailable) {
                            throw new ResumeException(HttpStatus.SERVICE_UNAVAILABLE,"UPLOAD_CONFIRMATION_FAILED","导入结果未能确认，请刷新并恢复未完成上传");
                        }
                    }
                    throw expectedFailure;
                }
                catch(RuntimeException databaseFailure) {
                    try { persistence.fail(owner,row.id(),row.uploadToken(),"UPLOAD_CONFIRMATION_FAILED"); }
                    catch(RuntimeException unavailable) { /* Durable reservation remains recoverable. */ }
                    throw new ResumeException(HttpStatus.SERVICE_UNAVAILABLE,"UPLOAD_CONFIRMATION_FAILED","导入结果未能确认，请刷新并恢复未完成上传");
                }
            } finally {
                // A late put cannot bind. Even a formerly DELETED uncertain tombstone is requeued.
                if(putConfirmed) {
                    try { persistence.completeIo(owner,row.id(),row.uploadToken()); }
                    catch(RuntimeException unavailable) { /* Recovery retains the key and uncertain tombstone. */ }
                }
                // A timed-out/failed provider call can still finish at the server. Its durable
                // uncertain key remains guarded until the finite deadline, then repeatedly deleted.
                activeIo.remove(row.id());
            }
        } catch(IOException failure) {
            throw new ResumeException(HttpStatus.SERVICE_UNAVAILABLE,"UPLOAD_TEMPORARY_IO","简历暂存不可用，请重试");
        }
    }
    private Receipt response(Receipt receipt) {
        if(!receipt.state().equals("FAILED")) return receipt;
        String code=receipt.safeFailureCode()==null?"UPLOAD_EXPIRED":receipt.safeFailureCode();
        HttpStatus status=code.equals("STORAGE_UNAVAILABLE") || code.equals("UPLOAD_CONFIRMATION_FAILED")
                ?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.CONFLICT;
        throw new ResumeException(status,code,"导入未完成，请刷新状态后使用新请求重试",receipt.resultVersion());
    }
    @Override public Download original() {
        var row=persistence.original(CurrentUser.requireId());
        try {
            var object=storage.open(row.storageKey());
            if(object.size()!=row.byteSize()) {
                try { object.close(); } catch(IOException ignored) {}
                throw new ResumeException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","原件暂不可读取，请重试");
            }
            return new Download(ResumePersistenceService.file(row),object);
        } catch(StorageException failure) {
            throw new ResumeException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","原件暂不可读取，请重试");
        }
    }
    @Override public Maintenance recover() { return persistence.recover(CurrentUser.requireId()); }
    @Override public Maintenance cleanup() { return cleanup(CurrentUser.requireId()); }
    private Maintenance cleanup(UUID owner) {
        List<Result> results=new ArrayList<>();
        for(var row:persistence.claimCleanup(owner,Set.copyOf(activeIo))) {
            boolean success;
            try { storage.delete(row.storageKey()); success=true; }
            catch(RuntimeException failure) { success=false; }
            results.add(persistence.finishCleanup(owner,row.id(),row.operationToken(),success));
        }
        return new Maintenance(persistence.current(owner).version(),results);
    }
    /** Repeated scanning also covers a process dying before its reservation expires. */
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void maintainOrphans() {
        for(UUID owner:persistence.maintenanceOwners()) {
            try { persistence.recover(owner); cleanup(owner); }
            catch(RuntimeException unavailable) { /* Retry durable state on the next scan. */ }
        }
    }
    public static void validateText(String text) {
        try { MarkdownText.validate(text); }
        catch(IOException invalid) { throw new ResumeException(HttpStatus.BAD_REQUEST,"RESUME_TEXT_INVALID","简历正文须为有效文本，且不含二进制控制字符"); }
        if(text.codePointCount(0,text.length())>20000)
            throw new ResumeException(HttpStatus.BAD_REQUEST,"RESUME_TEXT_TOO_LONG","简历正文不能超过 20,000 个字符");
    }
    private static void parameters(long version,UUID request) { if(version<0 || request==null) throw invalid(); }
    public static String hash(String text) { return payloadBytes(text.getBytes(StandardCharsets.UTF_8)); }
    /** Length framing avoids ambiguous concatenation of exact user-controlled fields. */
    public static String payload(String... fields) {
        try {
            var bytes=new ByteArrayOutputStream();
            try(var output=new DataOutputStream(bytes)) {
                for(String field:fields) { byte[] value=field.getBytes(StandardCharsets.UTF_8); output.writeInt(value.length); output.write(value); }
            }
            return payloadBytes(bytes.toByteArray());
        } catch(IOException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String payloadBytes(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static ResumeException invalid() { return new ResumeException(HttpStatus.BAD_REQUEST,"RESUME_REQUEST_INVALID","简历请求参数无效"); }
}
