package com.smartwealth.ai.service.model;

import java.util.List;

public record SpecializedAdvisoryResult(
        List<ProductRecommendation> candidateProducts,
        List<LlmProductSelection> finalRecommendations,
        List<InvestmentPlan> investmentPlans,
        List<String> investmentPlanSummaries,
        List<String> advisoryHighlights,
        String selectionSummary,
        String answer
) {
}
