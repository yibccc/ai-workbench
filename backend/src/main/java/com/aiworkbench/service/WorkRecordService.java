package com.aiworkbench.service;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.record.CreateWorkRecordRequest;
import com.aiworkbench.dto.record.UpdateWorkRecordRequest;
import com.aiworkbench.dto.record.WorkRecordResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkRecordService {
    WorkRecordResponse create(CreateWorkRecordRequest request);
    List<WorkRecordResponse> list(LocalDate date);
    PageResponse<WorkRecordResponse> page(LocalDate date, int page, int size);
    WorkRecordResponse get(UUID id);
    WorkRecordResponse update(UUID id, UpdateWorkRecordRequest request);
    void delete(UUID id);
}
