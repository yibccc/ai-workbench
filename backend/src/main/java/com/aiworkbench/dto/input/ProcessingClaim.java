package com.aiworkbench.dto.input;

import com.aiworkbench.entity.input.InputRow;
import java.util.UUID;

public record ProcessingClaim(InputRow row, UUID token, boolean owner) {}
