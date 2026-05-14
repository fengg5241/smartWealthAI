package com.smartwealth.ai.api.response;

public record FinalRecommendationView(
        String productCode,
        String productName,
        String reason
) {
}
