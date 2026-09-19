package com.aiworkbench.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateWorkRecordRequest(
        UUID projectId,
        @NotBlank(message = "工作内容不能为空")
        @Size(max = 4000, message = "工作内容不能超过 4000 个字符")
        String content,
        @NotNull(message = "发生时间不能为空")
        Instant occurredAt) {
}
