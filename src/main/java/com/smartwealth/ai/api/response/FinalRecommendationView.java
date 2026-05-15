package com.smartwealth.ai.api.response;

public record FinalRecommendationView(
        String productCode,
        String productName,
        String productDisplayName,
        boolean purchaseLinkEnabled,
        java.math.BigDecimal annualReturnRate,
        Integer minHoldingDays,
        String liquidityLevel,
        String complianceNote,
        String reason,
        InvestmentPlanView investmentPlan
) {
}
