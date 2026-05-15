package com.smartwealth.ai.service.model;

public record LlmProductSelection(
        ProductRecommendation product,
        String reason
) {
}
