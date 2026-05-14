package com.smartwealth.ai.api.response;

import com.smartwealth.ai.domain.RiskLevel;
import java.util.List;

public record ChatResponse(
        Long userId,
        RiskLevel riskLevel,
        List<MonthlyCashflowSummary> monthlySummaries,
        GoalProjectionView goalProjection,
        List<ProductRecommendationView> candidateProducts,
        List<FinalRecommendationView> finalRecommendedProducts,
        String llmSelectionSummary,
        List<String> advisoryHighlights,
        List<String> ragContextSnippets,
        String answer
) {
}
