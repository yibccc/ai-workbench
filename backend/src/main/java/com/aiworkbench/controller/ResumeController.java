package com.aiworkbench.controller;

import com.aiworkbench.dto.resume.ResumeModels.*;
import com.aiworkbench.exception.ResumeException;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.ResumeService;
import com.aiworkbench.storage.MarkdownText;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/resume")
public class ResumeController {
    private final ResumeService service;
    public ResumeController(ResumeService service) { this.service=service; }
    @GetMapping public Current current() { return service.current(); }
    @PutMapping public Receipt save(@RequestBody Save request) { return service.save(request); }
    @DeleteMapping public Receipt delete(@RequestParam long expectedVersion,@RequestParam UUID requestId) { return service.delete(expectedVersion,requestId); }
    @PostMapping(value="/import",consumes="multipart/form-data")
    public Receipt importMarkdown(HttpServletRequest request) throws IOException,ServletException {
        CurrentUser.requireId(); // Authenticate before lazy multipart resolution.
        Map<String,Part> parts=new HashMap<>();
        for(Part part:request.getParts()) {
            if(!Set.of("file","markdownText","expectedVersion","requestId").contains(part.getName()) || parts.putIfAbsent(part.getName(),part)!=null) throw invalid();
        }
        if(parts.size()!=4 || parts.get("file").getSubmittedFileName()==null) throw invalid();
        long version; UUID id;
        try { version=Long.parseLong(field(parts.get("expectedVersion"),100)); id=UUID.fromString(field(parts.get("requestId"),100)); }
        catch(IllegalArgumentException invalid) { throw invalid(); }
        String text=field(parts.get("markdownText"),80000);
        try(var input=parts.get("file").getInputStream()) {
            return service.importMarkdown(version,id,text,parts.get("file").getSubmittedFileName(),input);
        }
    }
    private String field(Part part,int limit) throws IOException {
        if(part.getSubmittedFileName()!=null || part.getSize()>limit) throw invalid();
        try(var input=part.getInputStream()) {
            byte[] bytes=input.readNBytes(limit+1); if(bytes.length>limit) throw invalid();
            try { return MarkdownText.decodePreservingBom(new ByteArrayInputStream(bytes)); }
            catch(IOException invalidText) { throw invalid(); }
        }
    }
    @RequestMapping(value="/original",method={RequestMethod.GET,RequestMethod.HEAD})
    public void original(HttpServletRequest request,HttpServletResponse response) throws IOException {
        var download=service.original();
        try(var object=download.object()) {
            response.setStatus(200); response.setContentType("text/markdown; charset=UTF-8");
            response.setContentLengthLong(download.file().size());
            response.setHeader("Cache-Control","no-store, private"); response.setHeader("X-Content-Type-Options","nosniff");
            response.setHeader("Accept-Ranges","none");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.file().fileName(),StandardCharsets.UTF_8).build().toString());
            if(!request.getMethod().equals("HEAD")) {
                byte[] buffer=new byte[16384]; int count;
                while((count=object.stream().read(buffer))!=-1) response.getOutputStream().write(buffer,0,count);
            }
        }
    }
    @PostMapping("/recover") public Maintenance recover() { return service.recover(); }
    @PostMapping("/cleanup") public Maintenance cleanup() { return service.cleanup(); }
    private ResumeException invalid() { return new ResumeException(HttpStatus.BAD_REQUEST,"RESUME_REQUEST_INVALID","导入必须包含一个 MD 原件、最终正文、版本和请求标识"); }
}
