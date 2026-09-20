package com.aiworkbench.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInputRequest(
        @NotBlank(message = "请求标识不能为空") @Size(max = 120, message = "请求标识不能超过 120 个字符") String requestId,
        @NotBlank(message = "输入内容不能为空") @Size(max = 8000, message = "输入内容不能超过 8000 个字符") String content) {}
