package com.aiworkbench.dto.report;

import com.aiworkbench.entity.report.ReportRow;
import java.util.UUID;

public record ReportClaim(ReportRow row, UUID token, boolean owner) {}
