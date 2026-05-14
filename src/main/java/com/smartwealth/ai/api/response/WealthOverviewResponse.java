package com.smartwealth.ai.api.response;

import com.smartwealth.ai.domain.RiskLevel;
import java.util.List;

public record WealthOverviewResponse(
        Long userId,
        RiskLevel riskLevel,
        List<MonthlyCashflowSummary> monthlySummaries,
        GoalProjectionView goalProjection,
        List<ProductRecommendationView> candidateProducts,
        List<String> advisoryHighlights
) {
}
