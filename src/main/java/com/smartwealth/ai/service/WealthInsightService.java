package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.GoalProjectionView;
import com.smartwealth.ai.api.response.GoalScenarioView;
import com.smartwealth.ai.api.response.InvestmentPlanView;
import com.smartwealth.ai.api.response.ProductRecommendationView;
import com.smartwealth.ai.domain.RiskLevel;
import com.smartwealth.ai.domain.UserProfile;
import com.smartwealth.ai.repository.UserProfileRepository;
import com.smartwealth.ai.service.model.GoalProjection;
import com.smartwealth.ai.service.model.GoalScenarioAnalysis;
import com.smartwealth.ai.service.model.InvestmentPlan;
import com.smartwealth.ai.service.model.PortfolioHoldingSnapshot;
import com.smartwealth.ai.service.model.ProductRecommendation;
import com.smartwealth.ai.service.model.RagSnippet;
import com.smartwealth.ai.service.model.SupportedLanguage;
import com.smartwealth.ai.service.model.WealthInsight;
import com.smartwealth.ai.service.model.WealthWorkflow;
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
    private final GoalScenarioService goalScenarioService;
    private final UserProfileRepository userProfileRepository;
    private final InvestmentPlanService investmentPlanService;
    private final ProductLinkFormatter productLinkFormatter;
    private final PortfolioQueryService portfolioQueryService;

    public WealthInsightService(
            TransactionAnalysisService transactionAnalysisService,
            GoalProjectionService goalProjectionService,
            RagKnowledgeService ragKnowledgeService,
            ProductRecommendationService productRecommendationService,
            AdvisoryNarrativeService advisoryNarrativeService,
            GoalScenarioService goalScenarioService,
            UserProfileRepository userProfileRepository,
            InvestmentPlanService investmentPlanService,
            ProductLinkFormatter productLinkFormatter,
            PortfolioQueryService portfolioQueryService
    ) {
        this.transactionAnalysisService = transactionAnalysisService;
        this.goalProjectionService = goalProjectionService;
        this.ragKnowledgeService = ragKnowledgeService;
        this.productRecommendationService = productRecommendationService;
        this.advisoryNarrativeService = advisoryNarrativeService;
        this.goalScenarioService = goalScenarioService;
        this.userProfileRepository = userProfileRepository;
        this.investmentPlanService = investmentPlanService;
        this.productLinkFormatter = productLinkFormatter;
        this.portfolioQueryService = portfolioQueryService;
    }

    public WealthInsight buildInsight(
            Long userId,
            String userQuestion,
            List<String> historyMessages,
            SupportedLanguage language,
            WealthWorkflow workflow
    ) {
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new com.smartwealth.ai.service.exception.ResourceNotFoundException("User not found: " + userId));
        String conversationContext = buildConversationContext(userQuestion, historyMessages);
        RiskLevel riskLevel = ragKnowledgeService.getRiskLevel(userId);
        var analyses = transactionAnalysisService.analyzeLastTwoMonths(userId);
        GoalProjection projection = goalProjectionService.project(userId, analyses);
        GoalScenarioAnalysis goalScenarioAnalysis = goalScenarioService.analyze(conversationContext, userProfile, projection);
        String preferredCurrency = goalScenarioAnalysis.currency();
        boolean recommendProducts = workflow.shouldRecommendProducts();
        List<ProductRecommendation> recommendations = recommendProducts
                ? productRecommendationService.recommend(riskLevel, projection, preferredCurrency, userProfile.getAvailableSavingsBalance())
                : List.of();
        if (recommendProducts && workflow.lowerRiskAlternativeOnly()) {
            recommendations = productRecommendationService.recommendLowerRiskAlternatives(riskLevel, projection, preferredCurrency, userProfile.getAvailableSavingsBalance());
        }
        List<InvestmentPlan> investmentPlans = recommendations.stream()
                .flatMap(item -> investmentPlanService.buildPlans(item, goalScenarioAnalysis, projection).stream())
                .toList();
        List<PortfolioHoldingSnapshot> portfolioHoldings = portfolioQueryService.holdings(userId);
        List<RagSnippet> ragSnippets = workflow.shouldRetrieveRag()
                ? ragKnowledgeService.retrieveUserContext(userId, conversationContext)
                : List.of();
        List<String> highlights = advisoryNarrativeService.buildHighlights(language, analyses, projection, recommendations);
        highlights.addAll(advisoryNarrativeService.buildSavingsAdvice(language, analyses));

        return new WealthInsight(
                userId,
                language,
                workflow,
                riskLevel,
                analyses,
                projection,
                goalScenarioAnalysis,
                recommendProducts,
                recommendations,
                investmentPlans,
                portfolioHoldings,
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

    public GoalScenarioView toView(GoalScenarioAnalysis scenario) {
        return new GoalScenarioView(
                scenario.scenarioName(),
                scenario.assetPrice(),
                scenario.requiredDownPayment(),
                scenario.currentSavingsBalance(),
                scenario.savingsGap(),
                scenario.affordableNow(),
                scenario.estimatedMonthsToReachGoal(),
                scenario.estimatedReachDate(),
                scenario.currency()
        );
    }

    public InvestmentPlanView toView(InvestmentPlan investmentPlan) {
        return new InvestmentPlanView(
                investmentPlan.productCode(),
                investmentPlan.productName(),
                investmentPlan.annualReturnRate(),
                investmentPlan.monthlyInvestmentAmount(),
                investmentPlan.savingsOnlyMonthsForSameContribution(),
                investmentPlan.investmentMonths(),
                investmentPlan.estimatedInvestmentGain(),
                investmentPlan.estimatedTotalValueAtGoalDate(),
                investmentPlan.estimatedReachDate(),
                investmentPlan.savedMonthsComparedToSavingOnly()
        );
    }

    private String buildConversationContext(String currentMessage, List<String> historyMessages) {
        StringBuilder builder = new StringBuilder();
        if (historyMessages != null && !historyMessages.isEmpty()) {
            builder.append(String.join("\n", historyMessages)).append('\n');
        }
        if (currentMessage != null) {
            builder.append(currentMessage);
        }
        return builder.toString().trim();
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
                productLinkFormatter.toDisplayName(recommendation.product().getProductName()),
                productLinkFormatter.purchaseLinkEnabled(),
                recommendation.product().getAnnualReturnRate(),
                recommendation.product().getMinHoldingDays(),
                recommendation.product().getLiquidityLevel().name(),
                recommendation.reason(),
                recommendation.product().getComplianceNote()
        );
    }
}
