package com.smartwealth.ai.service.model;

import com.smartwealth.ai.domain.RiskLevel;
import java.util.List;
import java.util.Map;

public record WealthInsight(
        Long userId,
        RiskLevel riskLevel,
        List<MonthlyAnalysis> monthlyAnalyses,
        GoalProjection goalProjection,
        GoalScenarioAnalysis goalScenarioAnalysis,
        boolean recommendProducts,
        List<ProductRecommendation> productRecommendations,
        List<InvestmentPlan> investmentPlans,
        List<String> advisoryHighlights,
        List<String> ragContextSnippets,
        List<RagSnippet> ragSnippets,
        Map<String, String> productNameByCode
) {
}
