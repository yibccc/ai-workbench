package com.aiworkbench.service.impl;

import com.aiworkbench.dto.community.AttachmentModels.*;
import com.aiworkbench.entity.community.AttachmentRow;
import com.aiworkbench.exception.AttachmentException;
import com.aiworkbench.mapper.AttachmentMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.AttachmentService;
import com.aiworkbench.storage.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Orchestration deliberately has no transaction across validation or provider calls. */
@Service
public class AttachmentServiceImpl implements AttachmentService {
    private final AttachmentPersistenceService persistence;
    private final AttachmentMapper attachments;
    private final AttachmentValidator validator;
    private final ObjectStorage storage;
    public AttachmentServiceImpl(AttachmentPersistenceService persistence, AttachmentMapper attachments,
            AttachmentValidator validator, ObjectStorage storage) {
        this.persistence=persistence; this.attachments=attachments; this.validator=validator; this.storage=storage;
    }
    @Override public void authorizeUpload(UUID postId) { persistence.authorize(CurrentUser.requireId(),postId); }
    @Override public Upload upload(UUID postId,long version,UUID requestId,String fileName,InputStream input) {
        UUID owner=CurrentUser.requireId(); persistence.authorize(owner,postId);
        if(version<0 || requestId==null) throw new AttachmentException(HttpStatus.BAD_REQUEST,"UPLOAD_REQUEST_INVALID","上传参数无效");
        try(var file=validator.stage(fileName,input)) {
            var reservation=persistence.reserve(owner,postId,version,requestId,file);
            var row=reservation.attachment();
            if(!reservation.created()) return response(row);
            try { storage.put(row.objectKey(),file.path(),file.size(),file.contentType(),file.sha256()); }
            catch(StorageException failure) { return response(persistence.fail(owner,postId,row.id(),row.attemptToken(),"STORAGE_UNAVAILABLE")); }
            try { return response(persistence.finish(owner,postId,row.id(),row.attemptToken())); }
            catch(AttachmentException failure) { throw failure; }
            catch(RuntimeException databaseFailure) {
                // The durable attempt/key remains recoverable even if this best-effort failure write also fails.
                Long currentVersion=null;
                try { currentVersion=persistence.fail(owner,postId,row.id(),row.attemptToken(),"UPLOAD_CONFIRMATION_FAILED").resultVersion(); }
                catch(RuntimeException unavailable) { /* Never invent READY when the DB cannot confirm it. */ }
                throw new AttachmentException(HttpStatus.SERVICE_UNAVAILABLE,"UPLOAD_CONFIRMATION_FAILED","上传结果未能确认，请刷新并恢复未完成上传",currentVersion);
            }
        } catch(java.io.IOException failure) {
            throw new AttachmentException(HttpStatus.SERVICE_UNAVAILABLE,"UPLOAD_TEMPORARY_IO","上传暂存不可用，请重试");
        }
    }
    private Upload response(AttachmentRow row) {
        if(row.state().equals("READY") || row.state().equals("UPLOADING")) return new Upload(row.info(),row.resultVersion());
        String code=row.safeFailureCode()==null || row.safeFailureCode().startsWith("DELETE_")?"UPLOAD_CANCELLED":row.safeFailureCode();
        throw new AttachmentException(code.equals("STORAGE_UNAVAILABLE") || code.equals("UPLOAD_CONFIRMATION_FAILED")
                ? HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.CONFLICT,code,"附件上传未完成，请恢复状态后使用新请求重试",row.resultVersion());
    }
    @Override public Download download(UUID postId,UUID id,boolean author) {
        UUID owner=CurrentUser.requireId();
        var row=(author?attachments.findOwnedReadable(owner,postId,id):attachments.findPublishedReadable(postId,id)).orElseThrow(
                ()->new AttachmentException(HttpStatus.NOT_FOUND,"ATTACHMENT_NOT_FOUND","附件不可访问"));
        try {
            var object=storage.open(row.objectKey());
            if(object.size()!=row.actualSize()) {
                try { object.close(); } catch(java.io.IOException ignored) { }
                throw new AttachmentException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","附件暂不可读取，请重试");
            }
            return new Download(row,object);
        } catch(StorageException failure) { throw new AttachmentException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","附件暂不可读取，请重试"); }
    }
    @Override public Maintenance recover(UUID postId) { return persistence.recover(CurrentUser.requireId(),postId); }
    @Override public Maintenance cleanup(UUID postId) {
        UUID owner=CurrentUser.requireId(); var results=new ArrayList<Result>();
        for(var row:persistence.claimCleanup(owner,postId)) {
            boolean success;
            try { storage.delete(row.objectKey()); success=true; }
            catch(StorageException failure) { success=false; }
            results.add(persistence.finishCleanup(owner,postId,row.id(),row.attemptToken(),success));
        }
        return new Maintenance(persistence.version(owner,postId),results);
    }
}
