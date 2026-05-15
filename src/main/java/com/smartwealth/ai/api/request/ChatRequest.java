package com.smartwealth.ai.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRequest(
        @NotNull Long userId,
        @NotBlank String message,
        String sessionId
) {
}
