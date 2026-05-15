package com.smartwealth.ai.api.response;

import com.smartwealth.ai.domain.RiskLevel;
import java.util.List;

public record ChatResponse(
        Long userId,
        String sessionId,
        ChatIntentType intentType,
        String intentReason,
        RiskLevel riskLevel,
        List<MonthlyCashflowSummary> monthlySummaries,
        GoalProjectionView goalProjection,
        GoalScenarioView goalScenario,
        List<ProductRecommendationView> candidateProducts,
        List<FinalRecommendationView> finalRecommendedProducts,
        List<InvestmentPlanView> investmentPlans,
        String llmSelectionSummary,
        List<String> advisoryHighlights,
        List<String> ragContextSnippets,
        String answer,
        List<ChatMessageView> messages
) {
}
