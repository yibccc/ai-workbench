package com.aiworkbench.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 120, message = "项目名称不能超过 120 个字符")
        String name) {
}
