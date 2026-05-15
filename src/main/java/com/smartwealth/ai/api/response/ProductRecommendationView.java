package com.smartwealth.ai.api.response;

import java.math.BigDecimal;

public record ProductRecommendationView(
        String productCode,
        String productName,
        String productDisplayName,
        boolean purchaseLinkEnabled,
        BigDecimal annualReturnRate,
        Integer minHoldingDays,
        String liquidityLevel,
        String reason,
        String complianceNote
) {
}
