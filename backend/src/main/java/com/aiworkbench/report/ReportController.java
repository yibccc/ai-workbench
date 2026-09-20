package com.aiworkbench.report;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
    @GetMapping("/{id}")
    public ReportResponse get(@PathVariable UUID id) { return service.get(id); }
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
