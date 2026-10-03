package com.aiworkbench.controller;

import com.aiworkbench.dto.community.AttachmentModels.*;
import com.aiworkbench.exception.AttachmentException;
import com.aiworkbench.service.AttachmentService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AttachmentController {
    private final AttachmentService service;
    public AttachmentController(AttachmentService service) { this.service=service; }

    @PostMapping(value="/api/me/posts/{postId}/attachments",consumes="multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public Upload upload(@PathVariable UUID postId,HttpServletRequest request) throws IOException,ServletException {
        // Lazy multipart has not resolved any @RequestParam/MultipartFile argument before this owner query.
        service.authorizeUpload(postId);
        Map<String,Part> parts=new HashMap<>();
        for(Part part:request.getParts()) {
            if(!java.util.Set.of("file","expectedVersion","requestId").contains(part.getName()) || parts.putIfAbsent(part.getName(),part)!=null) throw invalid();
        }
        if(parts.size()!=3 || parts.get("file").getSubmittedFileName()==null) throw invalid();
        long version; UUID id;
        try { version=Long.parseLong(field(parts.get("expectedVersion"))); id=UUID.fromString(field(parts.get("requestId"))); }
        catch(IllegalArgumentException failure) { throw invalid(); }
        try(var input=parts.get("file").getInputStream()) {
            return service.upload(postId,version,id,parts.get("file").getSubmittedFileName(),input);
        }
    }
    private String field(Part part) throws IOException {
        if(part.getSubmittedFileName()!=null || part.getSize()>100) throw invalid();
        try(var input=part.getInputStream()) { return new String(input.readNBytes(101),StandardCharsets.UTF_8); }
    }
    private ResponseStatusException invalid() { return new AttachmentException(HttpStatus.BAD_REQUEST,"UPLOAD_REQUEST_INVALID","上传必须包含一个文件、稿件版本和请求标识"); }

    @RequestMapping(value="/api/me/posts/{postId}/attachments/{attachmentId}",method={RequestMethod.GET,RequestMethod.HEAD})
    public void author(@PathVariable UUID postId,@PathVariable UUID attachmentId,HttpServletRequest request,HttpServletResponse response) throws IOException {
        stream(postId,attachmentId,true,request,response);
    }
    @RequestMapping(value="/api/community/posts/{postId}/attachments/{attachmentId}",method={RequestMethod.GET,RequestMethod.HEAD})
    public void reader(@PathVariable UUID postId,@PathVariable UUID attachmentId,HttpServletRequest request,HttpServletResponse response) throws IOException {
        stream(postId,attachmentId,false,request,response);
    }
    private void stream(UUID post,UUID id,boolean author,HttpServletRequest request,HttpServletResponse response) throws IOException {
        var download=service.download(post,id,author);
        try(var object=download.object()) {
            var row=download.attachment();
            response.setHeader("Cache-Control","no-store, private"); response.setHeader("X-Content-Type-Options","nosniff");
            response.setHeader("Accept-Ranges","none"); response.setContentType(row.verifiedContentType());
            response.setContentLengthLong(row.actualSize());
            response.setHeader("Content-Disposition",(row.verifiedContentType().startsWith("image/")?ContentDisposition.inline():ContentDisposition.attachment())
                    .filename(row.originalFilename(),StandardCharsets.UTF_8).build().toString());
            // Synchronous bounded copy owns the provider stream for its complete Servlet lifetime.
            if(!request.getMethod().equals("HEAD")) {
                byte[] buffer=new byte[16_384]; int count;
                while((count=object.stream().read(buffer))!=-1) response.getOutputStream().write(buffer,0,count);
            }
        }
    }
    @PostMapping("/api/me/posts/{postId}/attachments/recover")
    public Maintenance recover(@PathVariable UUID postId) { return service.recover(postId); }
    @PostMapping("/api/me/posts/{postId}/attachments/cleanup")
    public Maintenance cleanup(@PathVariable UUID postId) { return service.cleanup(postId); }
}
