package com.aiworkbench.service;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.report.CreateReportRequest;
import com.aiworkbench.dto.report.ReportResponse;
import com.aiworkbench.dto.report.UpdateManualAdditionsRequest;
import com.aiworkbench.dto.report.UpdateReportRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReportService {
    void delete(UUID id, long version);
    ReportResponse create(CreateReportRequest request);
    ReportResponse get(UUID id);
    List<ReportResponse> list(LocalDate date);
    List<ReportResponse> list(String reportType, LocalDate date);
    PageResponse<ReportResponse> page(String reportType, LocalDate date, int page, int size);
    PageResponse<ReportResponse.Source> sourcePage(UUID reportId, int page, int size);
    ReportResponse update(UUID id, UpdateReportRequest request);
    ReportResponse updateManualAdditions(UUID id, UpdateManualAdditionsRequest request);
}
