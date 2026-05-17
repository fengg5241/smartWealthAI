package com.smartwealth.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartwealth.ai.api.response.ChatIntentType;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntentRoutingServiceTest {

    private final IntentRoutingService intentRoutingService = new IntentRoutingService();

    @Test
    void shouldRouteEnglishQuestionToGeneralQa() {
        var intent = intentRoutingService.detect(
                "Can I afford the apartment down payment with my current savings?",
                List.of("我正在准备买房首付")
        );

        assertThat(intent.type()).isEqualTo(ChatIntentType.WEALTH_ADVISORY);
        assertThat(intent.intentCode().name()).isEqualTo("GOAL_FEASIBILITY");
    }

    @Test
    void shouldKeepChineseWealthQuestionInAdvisoryFlow() {
        var intent = intentRoutingService.detect(
                "我想看下首付够不够，顺便推荐合适的理财产品。",
                List.of()
        );

        assertThat(intent.type()).isEqualTo(ChatIntentType.WEALTH_ADVISORY);
    }

    @Test
    void shouldNotTreatMixedLanguageWealthQuestionAsEnglishOnly() {
        var intent = intentRoutingService.detect(
                "我的apartment首付够吗？",
                List.of()
        );

        assertThat(intent.type()).isEqualTo(ChatIntentType.WEALTH_ADVISORY);
    }

    @Test
    void shouldClassifyProductRecommendationRequest() {
        var result = intentRoutingService.classifyWithRules(
                "Please recommend a suitable product for my goal.",
                List.of()
        );

        assertThat(result.intentCode().name()).isEqualTo("PRODUCT_RECOMMENDATION");
    }

    @Test
    void shouldClassifyRiskRebalancingRequest() {
        var result = intentRoutingService.classifyWithRules(
                "The current product feels too risky. Show me lower risk alternatives.",
                List.of("Please recommend a product")
        );

        assertThat(result.intentCode().name()).isEqualTo("RISK_REBALANCING");
    }

    @Test
    void shouldDetectLanguageSeparatelyFromIntent() {
        assertThat(intentRoutingService.detectLanguage("Please analyze my savings goal")).isEqualTo(com.smartwealth.ai.service.model.SupportedLanguage.EN);
        assertThat(intentRoutingService.detectLanguage("请分析我的储蓄目标")).isEqualTo(com.smartwealth.ai.service.model.SupportedLanguage.ZH);
    }

    @Test
    void shouldClassifyEnglishCashflowQuestionWithoutFallingIntoProductRecommendation() {
        var result = intentRoutingService.classifyWithRules(
                "Please analyze my cash flow and spending this month.",
                List.of("Please recommend a product for my goal.")
        );

        assertThat(result.intentCode().name()).isEqualTo("CASHFLOW_ANALYSIS");
    }

    @Test
    void shouldClassifyEnglishRiskLevelQuestionAsOverviewInsteadOfProductRecommendation() {
        var result = intentRoutingService.classifyWithRules(
                "What is my current risk level?",
                List.of("Please recommend a product for my goal.")
        );

        assertThat(result.intentCode().name()).isEqualTo("WEALTH_OVERVIEW");
    }

    @Test
    void shouldClassifyEnglishGoalProgressQuestionWithoutProductBias() {
        var result = intentRoutingService.classifyWithRules(
                "How long will it take me to reach my savings goal?",
                List.of("Recommend a product for me.")
        );

        assertThat(result.intentCode().name()).isEqualTo("GOAL_PROGRESS");
    }

    @Test
    void shouldClassifyFundSelectionQuestion() {
        var result = intentRoutingService.classifyWithRules(
                "What is the best funds for $50,000?",
                List.of()
        );

        assertThat(result.intentCode().name()).isEqualTo("FUND_SELECTION");
    }

    @Test
    void shouldClassifyFixedDepositVsBondQuestion() {
        var result = intentRoutingService.classifyWithRules(
                "Fixed deposits or bonds is better for me?",
                List.of()
        );

        assertThat(result.intentCode().name()).isEqualTo("PRODUCT_COMPARISON");
    }

    @Test
    void shouldClassifyVolatileMarketPortfolioQuestion() {
        var result = intentRoutingService.classifyWithRules(
                "The market is so volatile now, how should I adjust my portfolio?",
                List.of()
        );

        assertThat(result.intentCode().name()).isEqualTo("PORTFOLIO_REBALANCING");
    }
}
