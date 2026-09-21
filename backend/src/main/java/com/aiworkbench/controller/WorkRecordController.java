package com.aiworkbench.controller;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.record.CreateWorkRecordRequest;
import com.aiworkbench.dto.record.UpdateWorkRecordRequest;
import com.aiworkbench.dto.record.WorkRecordResponse;
import com.aiworkbench.service.WorkRecordService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/records")
public class WorkRecordController {
    private final WorkRecordService service;

    public WorkRecordController(WorkRecordService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkRecordResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.list(date);
    }

    @GetMapping("/page")
    public PageResponse<WorkRecordResponse> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.page(date, page, size);
    }

    @PostMapping
    public WorkRecordResponse create(@Valid @RequestBody CreateWorkRecordRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public WorkRecordResponse get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/{id}")
    public WorkRecordResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkRecordRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
