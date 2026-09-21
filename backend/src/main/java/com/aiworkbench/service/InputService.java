package com.aiworkbench.service;

import com.aiworkbench.dto.input.CreateInputRequest;
import com.aiworkbench.dto.input.InputResponse;
import java.util.UUID;

public interface InputService {
    InputResponse create(CreateInputRequest request);
    InputResponse retry(UUID id);
    InputResponse revert(UUID id);
    InputResponse get(UUID id);
}
