package com.aiworkbench.controller;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.report.CreateReportRequest;
import com.aiworkbench.dto.report.ReportResponse;
import com.aiworkbench.dto.report.UpdateManualAdditionsRequest;
import com.aiworkbench.dto.report.UpdateReportRequest;
import com.aiworkbench.service.ReportService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@Valid @RequestBody CreateReportRequest request) { return service.create(request); }
    @GetMapping
    public List<ReportResponse> list(@RequestParam(required = false) LocalDate date,
                                     @RequestParam(required = false, defaultValue = "DAILY") String reportType) {
        return service.list(reportType, date);
    }
    @GetMapping("/page")
    public PageResponse<ReportResponse> page(@RequestParam(required = false) LocalDate date,
            @RequestParam(required = false, defaultValue = "DAILY") String reportType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.page(reportType, date, page, size);
    }
    @GetMapping("/{id}/sources/page")
    public PageResponse<ReportResponse.Source> sources(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.sourcePage(id, page, size);
    }
    @GetMapping("/{id}")
    public ReportResponse get(@PathVariable UUID id) { return service.get(id); }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam long version) { service.delete(id, version); }
    @PatchMapping("/{id}")
    public ReportResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateReportRequest request) {
        return service.update(id, request);
    }
    @PatchMapping("/{id}/manual-additions")
    public ReportResponse updateManualAdditions(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateManualAdditionsRequest request) {
        return service.updateManualAdditions(id, request);
    }
}
