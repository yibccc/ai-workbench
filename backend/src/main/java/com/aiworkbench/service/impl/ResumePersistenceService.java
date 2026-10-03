package com.aiworkbench.service.impl;

import com.aiworkbench.config.StorageProperties;
import com.aiworkbench.dto.resume.ResumeModels;
import com.aiworkbench.dto.resume.ResumeModels.*;
import com.aiworkbench.entity.resume.ResumeRows.*;
import com.aiworkbench.exception.ResumeException;
import com.aiworkbench.mapper.ResumeMapper;
import com.aiworkbench.storage.ValidatedAttachment;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Lock owner account, current, objects, then receipts. No external I/O in this bean. */
@Service
public class ResumePersistenceService {
    private final ResumeMapper mapper;
    private final StorageProperties properties;
    public ResumePersistenceService(ResumeMapper mapper, StorageProperties properties) {
        this.mapper=mapper; this.properties=properties;
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ResumeModels.Current current(UUID owner) {
        var current=mapper.current(owner);
        if(current.isEmpty()) return new ResumeModels.Current(false,0,null,null,null);
        var row=current.get();
        var original=mapper.original(owner).map(ResumePersistenceService::file).orElse(null);
        return new ResumeModels.Current(row.markdownText()!=null,row.version(),row.markdownText(),row.sourceKind(),original);
    }
    /** Caller freezes this return value in its own creation transaction, before model I/O. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Snapshot snapshotForInterview(UUID owner, long expectedVersion) {
        lockOwner(owner);
        var row=mapper.lockCurrent(owner);
        long version=row.map(com.aiworkbench.entity.resume.ResumeRows.Current::version).orElse(0L);
        if(version!=expectedVersion) throw conflict(version);
        return row.map(r->new Snapshot(r.markdownText()!=null,r.version(),r.markdownText(),r.contentSha256()))
                .orElse(new Snapshot(false,0,null,null));
    }
    @Transactional(readOnly=true)
    public ObjectRow original(UUID owner) { return mapper.original(owner).orElseThrow(ResumePersistenceService::notFound); }
    @Transactional
    public Receipt save(UUID owner, Save request, String payload, String contentHash) {
        var current=lock(owner);
        var prior=prior(owner,"PUT",request.requestId(),payload,current.version());
        if(prior!=null) return receipt(prior);
        expected(current,request.expectedVersion());
        if(request.mode()==Mode.EDIT_CURRENT && current.markdownText()==null)
            throw new ResumeException(HttpStatus.CONFLICT,"RESUME_NOT_CURRENT","当前没有可编辑的简历",current.version());
        mapper.lockObjects(owner);
        String source=request.mode()==Mode.PASTE?"PASTE":current.sourceKind();
        UUID object=request.mode()==Mode.PASTE?null:current.currentObjectId();
        update(new com.aiworkbench.entity.resume.ResumeRows.Current(owner,current.version()+1,request.markdownText(),source,object,contentHash,now()),current.version());
        var result=new ReceiptRow(owner,"PUT",request.requestId(),payload,"SUCCEEDED",current.version()+1,null,null,now());
        mapper.insertReceipt(result);
        return receipt(result);
    }
    @Transactional
    public Receipt delete(UUID owner,long expected,UUID request,String payload) {
        var current=lock(owner); var prior=prior(owner,"DELETE",request,payload,current.version());
        if(prior!=null) return receipt(prior);
        expected(current,expected); mapper.lockObjects(owner);
        update(new com.aiworkbench.entity.resume.ResumeRows.Current(owner,current.version()+1,null,null,null,null,now()),current.version());
        var result=new ReceiptRow(owner,"DELETE",request,payload,"SUCCEEDED",current.version()+1,null,null,now());
        mapper.insertReceipt(result); return receipt(result);
    }
    @Transactional
    public Reservation reserve(UUID owner,long expected,UUID request,String payload,ValidatedAttachment file) {
        var current=lock(owner); var prior=prior(owner,"IMPORT",request,payload,current.version());
        if(prior!=null) return new Reservation(receipt(prior),null,false);
        expected(current,expected); mapper.lockObjects(owner);
        Instant now=now(); UUID id=UUID.randomUUID(),token=UUID.randomUUID(); Instant deadline=now.plus(properties.reservationDuration());
        var object=new ObjectRow(id,owner,request,"interview/resumes/"+owner+"/"+id+".md",file.fileName(),file.contentType(),file.size(),file.sha256(),
                "UPLOADING",token,token,deadline,deadline,true,null,now,now);
        mapper.insertObject(object);
        var result=new ReceiptRow(owner,"IMPORT",request,payload,"UPLOADING",null,id,null,now);
        mapper.insertReceipt(result);
        return new Reservation(receipt(result),object,true);
    }
    @Transactional
    public Receipt finish(UUID owner,UUID id,UUID token,long expected,String text,String hash) {
        var current=lock(owner); var object=object(mapper.lockObjects(owner),id);
        var prior=mapper.receipt(owner,"IMPORT",object.requestId()).orElseThrow();
        if(!prior.state().equals("UPLOADING")) return receipt(prior);
        if(!object.status().equals("UPLOADING") || !object.operationToken().equals(token)) return receipt(prior);
        if(!object.leaseExpiresAt().isAfter(now()) || current.version()!=expected) {
            return failLocked(current,object,"UPLOAD_EXPIRED");
        }
        update(new com.aiworkbench.entity.resume.ResumeRows.Current(owner,current.version()+1,text,"MD_FILE",id,hash,now()),current.version());
        mapper.finishReceipt(owner,object.requestId(),"SUCCEEDED",current.version()+1,null);
        // Fence after every child write: a slow current/receipt statement cannot consume the
        // remaining lease and still commit. Zero rows roll back current and receipt together.
        if(mapper.objectState(owner,id,"UPLOADING",token,"READY",null,now())!=1)
            throw new ResumeException(HttpStatus.CONFLICT,"UPLOAD_EXPIRED","导入确认执行权已过期，请刷新后重试",current.version());
        return receipt(mapper.receipt(owner,"IMPORT",object.requestId()).orElseThrow());
    }
    @Transactional
    public Receipt fail(UUID owner,UUID id,UUID token,String code) {
        var current=lock(owner); var object=object(mapper.lockObjects(owner),id);
        if(object.status().equals("UPLOADING") && object.operationToken().equals(token)) return failLocked(current,object,code);
        return receipt(mapper.receipt(owner,"IMPORT",object.requestId()).orElseThrow());
    }
    private Receipt failLocked(com.aiworkbench.entity.resume.ResumeRows.Current current,ObjectRow row,String code) {
        if(row.status().equals("UPLOADING")) mapper.objectState(row.userId(),row.id(),"UPLOADING",row.operationToken(),"FAILED",code,now());
        mapper.finishReceipt(row.userId(),row.requestId(),"FAILED",current.version(),code);
        return receipt(mapper.receipt(row.userId(),"IMPORT",row.requestId()).orElseThrow());
    }
    @Transactional
    public void completeIo(UUID owner,UUID id,UUID token) {
        lock(owner); mapper.lockObjects(owner); mapper.completeIo(owner,id,token,now());
    }
    @Transactional
    public Maintenance recover(UUID owner) {
        var current=lock(owner); List<Result> results=new ArrayList<>(); Instant now=now();
        for(var object:mapper.lockObjects(owner)) if(object.status().equals("UPLOADING") && !object.leaseExpiresAt().isAfter(now)) {
            failLocked(current,object,"UPLOAD_EXPIRED"); results.add(new Result(object.id(),"FAILED","UPLOAD_EXPIRED"));
        }
        return new Maintenance(current.version(),results);
    }
    @Transactional
    public List<ObjectRow> claimCleanup(UUID owner,Set<UUID> active) {
        var current=lock(owner); List<ObjectRow> result=new ArrayList<>(); Instant now=now();
        for(var row:mapper.lockObjects(owner)) {
            if(active.contains(row.id()) || Objects.equals(current.currentObjectId(),row.id()) || row.status().equals("UPLOADING")) continue;
            if(row.ioUncertain() && row.cleanupAfter().isAfter(now)) continue;
            if(row.status().equals("DELETING") && row.leaseExpiresAt().isAfter(now)) continue;
            if(row.status().equals("DELETED") && !row.ioUncertain()) continue;
            UUID token=UUID.randomUUID();
            if(mapper.claimDelete(owner,row.id(),token,now.plus(properties.reservationDuration()),now)==1)
                result.add(mapper.object(owner,row.id()).orElseThrow());
        }
        return result;
    }
    @Transactional
    public Result finishCleanup(UUID owner,UUID id,UUID token,boolean success) {
        lock(owner); var row=object(mapper.lockObjects(owner),id);
        if(row.status().equals("DELETING") && row.operationToken().equals(token)) {
            String state=success?"DELETED":"DELETE_FAILED",code=success?null:"STORAGE_UNAVAILABLE";
            mapper.objectState(owner,id,"DELETING",token,state,code,now());
            return new Result(id,state,code);
        }
        return new Result(id,row.status(),row.safeFailureCode());
    }
    @Transactional(readOnly=true)
    public List<UUID> maintenanceOwners() { return mapper.maintenanceOwners(); }
    private com.aiworkbench.entity.resume.ResumeRows.Current lock(UUID owner) {
        lockOwner(owner); mapper.ensureSlot(owner,now()); return mapper.lockCurrent(owner).orElseThrow();
    }
    private void lockOwner(UUID owner) { mapper.lockOwner(owner).orElseThrow(ResumePersistenceService::notFound); }
    private void expected(com.aiworkbench.entity.resume.ResumeRows.Current row,long version) { if(row.version()!=version) throw conflict(row.version()); }
    private void update(com.aiworkbench.entity.resume.ResumeRows.Current row,long expected) {
        if(mapper.saveCurrent(row,expected)!=1) throw conflict(expected);
    }
    private ReceiptRow prior(UUID owner,String operation,UUID request,String payload,long version) {
        var prior=mapper.receipt(owner,operation,request).orElse(null);
        if(prior!=null && !prior.payloadHash().equals(payload))
            throw new ResumeException(HttpStatus.CONFLICT,"RESUME_REQUEST_CONFLICT","同一请求标识不能用于不同内容",version);
        return prior;
    }
    private ObjectRow object(List<ObjectRow> objects,UUID id) { return objects.stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(ResumePersistenceService::notFound); }
    public static Receipt receipt(ReceiptRow r) { return new Receipt(r.requestId(),r.operation(),r.state(),r.resultVersion(),r.objectId(),r.safeFailureCode()); }
    public static OriginalFile file(ObjectRow r) { return new OriginalFile(r.id(),r.originalFilename(),r.byteSize(),r.sha256()); }
    private static ResumeException conflict(long version) { return new ResumeException(HttpStatus.CONFLICT,"VERSION_CONFLICT","当前简历已变化，请刷新后重试",version); }
    private static ResumeException notFound() { return new ResumeException(HttpStatus.NOT_FOUND,"RESUME_NOT_FOUND","简历或原件不可访问"); }
    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
    public record Reservation(Receipt receipt,ObjectRow object,boolean created) {}
}
