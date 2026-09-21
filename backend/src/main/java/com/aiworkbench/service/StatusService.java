package com.aiworkbench.service;

import com.aiworkbench.dto.status.WorkbenchStatus;

public interface StatusService {
    WorkbenchStatus current();
    String probe();
}
