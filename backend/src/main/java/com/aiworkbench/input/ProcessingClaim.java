package com.aiworkbench.input;

import java.util.UUID;

record ProcessingClaim(InputRow row, UUID token, boolean owner) {}
