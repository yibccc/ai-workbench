package com.aiworkbench.controller;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.interview.InterviewModels.*;
import com.aiworkbench.service.InterviewService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final InterviewService service;
    public InterviewController(InterviewService service) { this.service=service; }
    @PostMapping public Receipt create(@RequestBody Create request) { return service.create(request); }
    @GetMapping("/page") public PageResponse<Summary> page(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.page(page,size); }
    @PostMapping("/jd/parse") @ResponseStatus(HttpStatus.ACCEPTED) public JdAnalysis parseJd(@RequestBody ParseJd request) { return service.parseJd(request); }
    @GetMapping("/jd/{id}") public JdAnalysis jd(@PathVariable UUID id) { return service.jd(id); }
    @PostMapping("/jd/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED) public JdAnalysis retryJd(@PathVariable UUID id,@RequestBody VersionOperation request) { return service.retryJd(id,request); }
    @DeleteMapping("/jd/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteJd(@PathVariable UUID id) { service.deleteJd(id); }
    @GetMapping("/{id}") public Session session(@PathVariable UUID id) { return service.session(id); }
    @PutMapping("/{id}/answer-draft") public Receipt draft(@PathVariable UUID id,@RequestBody AnswerOperation request) { return service.draft(id,request); }
    @PostMapping("/{id}/answers/{turn}/submit") public Receipt submit(@PathVariable UUID id,@PathVariable int turn,@RequestBody AnswerOperation request) { return service.submit(id,turn,request); }
    @PostMapping("/{id}/complete") public Receipt complete(@PathVariable UUID id,@RequestBody Complete request) { return service.complete(id,request); }
    @PostMapping("/{id}/generation/retry") public Receipt retryGeneration(@PathVariable UUID id,@RequestBody VersionOperation request) { return service.retry(id,request,true); }
    @PostMapping("/{id}/evaluation/retry") public Receipt retryEvaluation(@PathVariable UUID id,@RequestBody VersionOperation request) { return service.retry(id,request,false); }
    @GetMapping("/{id}/report") public Report report(@PathVariable UUID id) { return service.report(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
}
