package com.aiworkbench.report;

import java.util.UUID;

record ReportClaim(ReportRow row, UUID token, boolean owner) {}
