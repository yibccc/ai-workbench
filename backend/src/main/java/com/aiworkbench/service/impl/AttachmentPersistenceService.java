package com.aiworkbench.service.impl;

import com.aiworkbench.config.StorageProperties;
import com.aiworkbench.dto.community.AttachmentModels.*;
import com.aiworkbench.entity.community.AttachmentRow;
import com.aiworkbench.entity.community.CommunityRows.Post;
import com.aiworkbench.exception.AttachmentException;
import com.aiworkbench.mapper.AttachmentMapper;
import com.aiworkbench.mapper.CommunityPostMapper;
import com.aiworkbench.storage.ValidatedAttachment;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Every mutation locks post, then attachment IDs. No provider I/O belongs in this bean. */
@Service
public class AttachmentPersistenceService {
    public static final int MAX_COUNT = 10;
    public static final long MAX_TOTAL = 52_428_800;
    private static final Pattern URI = Pattern.compile("attachment:([^\\s)\\]>'\"]*)");
    private final CommunityPostMapper posts;
    private final AttachmentMapper attachments;
    private final StorageProperties properties;
    public AttachmentPersistenceService(CommunityPostMapper posts, AttachmentMapper attachments, StorageProperties properties) {
        this.posts=posts; this.attachments=attachments; this.properties=properties;
    }
    @Transactional(readOnly=true)
    public void authorize(UUID owner, UUID post) {
        posts.findOwned(owner,post).orElseThrow(AttachmentPersistenceService::notFound);
    }
    @Transactional
    public Reservation reserve(UUID owner, UUID postId, long expectedVersion, UUID request, ValidatedAttachment file) {
        Post post=lock(owner,postId);
        var prior=attachments.findRequest(owner,postId,request);
        if(prior.isPresent()) {
            AttachmentRow row=prior.get();
            if(!row.sha256().equals(file.sha256()) || row.actualSize()!=file.size()
                    || !row.originalFilename().equals(file.fileName()) || !row.verifiedContentType().equals(file.contentType())) {
                throw new AttachmentException(HttpStatus.CONFLICT,"UPLOAD_REQUEST_CONFLICT","同一上传请求不能用于不同文件",post.version());
            }
            return new Reservation(row,false);
        }
        if(post.version()!=expectedVersion) throw new AttachmentException(HttpStatus.CONFLICT,"VERSION_CONFLICT","稿件已变化，请刷新后重试",post.version());
        var selected=attachments.findDraft(owner,postId);
        quota(selected.size()+1,selected.stream().mapToLong(AttachmentRow::actualSize).sum()+file.size(),post.version());
        Instant now=now(); UUID id=UUID.randomUUID();
        var row=new AttachmentRow(id,postId,owner,request,"community/attachments/"+id,file.fileName(),file.contentType(),file.size(),file.sha256(),
                "UPLOADING",UUID.randomUUID(),now.plus(properties.reservationDuration()),post.version()+1,null,now,now);
        attachments.insert(row);
        // Positions may have gaps after a failed reservation. Keep surviving order.
        int position=selected.size();
        if(!selected.isEmpty()) {
            attachments.deleteDraft(owner,postId);
            for(int i=0;i<selected.size();i++) attachments.insertDraft(owner,postId,selected.get(i).id(),i);
        }
        attachments.insertDraft(owner,postId,id,position);
        bump(post,now);
        return new Reservation(row,true);
    }
    @Transactional
    public AttachmentRow finish(UUID owner,UUID postId,UUID id,UUID token) {
        Post post=lock(owner,postId);
        var row=row(attachments.lockAll(owner,postId),id);
        if(!row.state().equals("UPLOADING") || !row.attemptToken().equals(token)) return row;
        boolean selected=attachments.findDraft(owner,postId).stream().anyMatch(a->a.id().equals(id));
        if(!selected || !row.reservationExpiresAt().isAfter(now())) return failLocked(post,row,"UPLOAD_EXPIRED");
        attachments.changeState(owner,postId,id,"UPLOADING","READY",token,null,row.resultVersion(),now());
        return requireRequest(row);
    }
    @Transactional
    public AttachmentRow fail(UUID owner,UUID postId,UUID id,UUID token,String code) {
        Post post=lock(owner,postId); var row=row(attachments.lockAll(owner,postId),id);
        if(!row.state().equals("UPLOADING") || !row.attemptToken().equals(token)) return row;
        return failLocked(post,row,code);
    }
    private AttachmentRow failLocked(Post post,AttachmentRow row,String code) {
        boolean changed=attachments.deleteDraftOne(post.ownerId(),post.id(),row.id())>0;
        long result=changed?post.version()+1:post.version();
        attachments.changeState(post.ownerId(),post.id(),row.id(),"UPLOADING","FAILED",row.attemptToken(),code,result,now());
        if(changed) bump(post,now());
        return requireRequest(row);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void replaceDraft(Post post,String body,List<UUID> ids) {
        List<AttachmentRow> all=attachments.lockAll(post.ownerId(),post.id());
        Map<UUID,AttachmentRow> byId=new HashMap<>(); all.forEach(a->byId.put(a.id(),a));
        long total=0;
        for(UUID id:ids) {
            var row=byId.get(id); if(row==null) throw notFound();
            if(!row.state().equals("READY")) throw new AttachmentException(HttpStatus.CONFLICT,"ATTACHMENT_NOT_READY","请先完成附件上传",post.version());
            total+=row.actualSize();
        }
        quota(ids.size(),total,post.version());
        validateBody(body,new HashSet<>(ids));
        Set<UUID> old= new HashSet<>(); attachments.findDraft(post.ownerId(),post.id()).forEach(a->old.add(a.id()));
        for(var row:all) if(row.state().equals("UPLOADING") && old.contains(row.id()) && !ids.contains(row.id())) {
            attachments.changeState(post.ownerId(),post.id(),row.id(),"UPLOADING","FAILED",row.attemptToken(),"UPLOAD_CANCELLED",post.version()+1,now());
        }
        attachments.deleteDraft(post.ownerId(),post.id());
        for(int i=0;i<ids.size();i++) attachments.insertDraft(post.ownerId(),post.id(),ids.get(i),i);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void bindRevision(Post post,UUID revision,List<UUID> ids) {
        for(int i=0;i<ids.size();i++) attachments.insertRevision(post.ownerId(),post.id(),revision,ids.get(i),i);
    }
    public static void validateBody(String body,Set<UUID> ids) {
        var matcher=URI.matcher(body);
        while(matcher.find()) {
            try {
                String value=matcher.group(1); UUID id=UUID.fromString(value);
                if(!id.toString().equalsIgnoreCase(value) || !ids.contains(id)) throw new IllegalArgumentException();
            } catch(IllegalArgumentException invalid) {
                throw new AttachmentException(HttpStatus.BAD_REQUEST,"ATTACHMENT_REFERENCE_INVALID","正文引用了未选择的附件");
            }
        }
    }
    @Transactional
    public Maintenance recover(UUID owner,UUID postId) {
        Post post=lock(owner,postId); var all=attachments.lockAll(owner,postId);
        List<Result> results=new ArrayList<>(); boolean changed=false; Instant now=now();
        for(var row:all) if(row.state().equals("UPLOADING") && !row.reservationExpiresAt().isAfter(now)) {
            changed |= attachments.deleteDraftOne(owner,postId,row.id())>0;
            attachments.changeState(owner,postId,row.id(),"UPLOADING","FAILED",row.attemptToken(),"UPLOAD_EXPIRED",post.version()+1,now);
            results.add(new Result(row.id(),"FAILED","UPLOAD_EXPIRED"));
        }
        if(changed) bump(post,now);
        return new Maintenance(post.version()+(changed?1:0),results);
    }
    @Transactional
    public List<AttachmentRow> claimCleanup(UUID owner,UUID postId) {
        Post post=lock(owner,postId); List<AttachmentRow> claimed=new ArrayList<>(); Instant now=now();
        for(var row:attachments.lockAll(owner,postId)) {
            if(row.state().equals("UPLOADING") || attachments.referenced(owner,postId,row.id())) continue;
            if(row.state().equals("DELETING") && row.reservationExpiresAt().isAfter(now)) continue;
            // A cancelled put can still be in flight until its finite reservation deadline.
            if(row.state().equals("FAILED") && row.reservationExpiresAt().isAfter(now)) continue;
            attachments.claimDelete(owner,postId,row.id(),UUID.randomUUID(),now.plus(properties.reservationDuration()),now);
            claimed.add(requireRequest(row));
        }
        return claimed;
    }
    @Transactional
    public Result finishCleanup(UUID owner,UUID postId,UUID id,UUID token,boolean success) {
        lock(owner,postId); var row=row(attachments.lockAll(owner,postId),id);
        String state=success?"DELETED":"DELETE_FAILED",code=success?null:"STORAGE_UNAVAILABLE";
        if(row.state().equals("DELETING") && row.attemptToken().equals(token)) {
            // Cleanup state cannot erase the original FAILED-upload receipt for requestId replay.
            String retainedCode=row.safeFailureCode()!=null?row.safeFailureCode():(success?null:"DELETE_STORAGE_UNAVAILABLE");
            attachments.changeState(owner,postId,id,"DELETING",state,token,retainedCode,row.resultVersion(),now());
            return new Result(id,state,code);
        }
        return new Result(id,row.state(),row.safeFailureCode());
    }
    @Transactional(readOnly=true)
    public long version(UUID owner,UUID postId) { return posts.findOwned(owner,postId).orElseThrow(AttachmentPersistenceService::notFound).version(); }
    private Post lock(UUID owner,UUID post) { return posts.lockOwned(owner,post).orElseThrow(AttachmentPersistenceService::notFound); }
    private AttachmentRow row(List<AttachmentRow> rows,UUID id) { return rows.stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(AttachmentPersistenceService::notFound); }
    private AttachmentRow requireRequest(AttachmentRow row) { return attachments.findRequest(row.ownerId(),row.postId(),row.requestId()).orElseThrow(); }
    private void bump(Post post,Instant now) {
        if(posts.updateAggregate(post.ownerId(),post.id(),post.version(),post.status(),post.currentRevisionId(),post.firstPublishedAt(),now)!=1)
            throw new AttachmentException(HttpStatus.CONFLICT,"VERSION_CONFLICT","稿件已变化，请刷新后重试");
    }
    private static void quota(int count,long size,long version) {
        if(count>MAX_COUNT || size>MAX_TOTAL) throw new AttachmentException(HttpStatus.CONFLICT,"ATTACHMENT_QUOTA","每篇最多 10 个附件且合计不超过 50 MiB",version);
    }
    private static AttachmentException notFound() { return new AttachmentException(HttpStatus.NOT_FOUND,"ATTACHMENT_NOT_FOUND","稿件或附件不存在"); }
    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
    public record Reservation(AttachmentRow attachment,boolean created) {}
}
