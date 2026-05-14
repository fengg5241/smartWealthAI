package com.smartwealth.ai.api.response;

import java.math.BigDecimal;

public record ProductRecommendationView(
        String productCode,
        String productName,
        BigDecimal annualReturnRate,
        Integer minHoldingDays,
        String liquidityLevel,
        String reason,
        String complianceNote
) {
}
