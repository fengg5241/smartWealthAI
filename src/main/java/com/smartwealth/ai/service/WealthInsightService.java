package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.GoalProjectionView;
import com.smartwealth.ai.api.response.ProductRecommendationView;
import com.smartwealth.ai.domain.RiskLevel;
import com.smartwealth.ai.service.model.GoalProjection;
import com.smartwealth.ai.service.model.ProductRecommendation;
import com.smartwealth.ai.service.model.RagSnippet;
import com.smartwealth.ai.service.model.WealthInsight;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WealthInsightService {

    private final TransactionAnalysisService transactionAnalysisService;
    private final GoalProjectionService goalProjectionService;
    private final RagKnowledgeService ragKnowledgeService;
    private final ProductRecommendationService productRecommendationService;
    private final AdvisoryNarrativeService advisoryNarrativeService;

    public WealthInsightService(
            TransactionAnalysisService transactionAnalysisService,
            GoalProjectionService goalProjectionService,
            RagKnowledgeService ragKnowledgeService,
            ProductRecommendationService productRecommendationService,
            AdvisoryNarrativeService advisoryNarrativeService
    ) {
        this.transactionAnalysisService = transactionAnalysisService;
        this.goalProjectionService = goalProjectionService;
        this.ragKnowledgeService = ragKnowledgeService;
        this.productRecommendationService = productRecommendationService;
        this.advisoryNarrativeService = advisoryNarrativeService;
    }

    public WealthInsight buildInsight(Long userId, String userQuestion) {
        RiskLevel riskLevel = ragKnowledgeService.getRiskLevel(userId);
        var analyses = transactionAnalysisService.analyzeLastTwoMonths(userId);
        GoalProjection projection = goalProjectionService.project(userId, analyses);
        List<ProductRecommendation> recommendations = productRecommendationService.recommend(riskLevel, projection);
        List<RagSnippet> ragSnippets = ragKnowledgeService.retrieveUserContext(userId, userQuestion);
        List<String> highlights = advisoryNarrativeService.buildHighlights(analyses, projection, recommendations);

        return new WealthInsight(
                userId,
                riskLevel,
                analyses,
                projection,
                recommendations,
                highlights,
                ragSnippets.stream().map(RagSnippet::text).toList(),
                ragSnippets,
                recommendations.stream()
                        .collect(Collectors.toMap(
                                item -> item.product().getProductCode(),
                                item -> item.product().getProductName(),
                                (left, right) -> left
                        ))
        );
    }

    public GoalProjectionView toView(GoalProjection projection) {
        return new GoalProjectionView(
                projection.goalName(),
                projection.targetAmount(),
                projection.targetDate(),
                projection.averageMonthlySavings(),
                projection.projectedMonthlyContribution(),
                projection.monthsToGoal(),
                projection.projectedCompletionDate(),
                projection.onTrack()
        );
    }

    public ProductRecommendationView toView(ProductRecommendation recommendation) {
        return new ProductRecommendationView(
                recommendation.product().getProductCode(),
                recommendation.product().getProductName(),
                recommendation.product().getAnnualReturnRate(),
                recommendation.product().getMinHoldingDays(),
                recommendation.product().getLiquidityLevel().name(),
                recommendation.reason(),
                recommendation.product().getComplianceNote()
        );
    }
}
