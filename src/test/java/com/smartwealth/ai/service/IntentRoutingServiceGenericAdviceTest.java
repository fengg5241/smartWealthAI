package com.smartwealth.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntentRoutingServiceGenericAdviceTest {

    private final IntentRoutingService intentRoutingService = new IntentRoutingService();

    @Test
    void shouldTreatInvestmentAdviceAsGenericWealthGuidance() {
        var result = intentRoutingService.classifyWithRules(
                "give me some investment advice",
                java.util.List.of()
        );

        assertThat(result.intentCode().name()).isEqualTo("WEALTH_OVERVIEW");
        assertThat(result.responsePolicy().name()).isEqualTo("GENERIC_WEALTH_GUIDANCE");
    }

    @Test
    void shouldTreatHowToStartInvestingAsGenericWealthGuidance() {
        var result = intentRoutingService.classifyWithRules(
                "How to start investing with $10,000?",
                java.util.List.of()
        );

        assertThat(result.intentCode().name()).isEqualTo("WEALTH_OVERVIEW");
        assertThat(result.responsePolicy().name()).isEqualTo("GENERIC_WEALTH_GUIDANCE");
    }

    @Test
    void shouldTreatChineseInvestmentAdviceAsGenericWealthGuidance() {
        var result = intentRoutingService.classifyWithRules(
                "给我一些投资建议",
                java.util.List.of()
        );

        assertThat(result.intentCode().name()).isEqualTo("WEALTH_OVERVIEW");
        assertThat(result.responsePolicy().name()).isEqualTo("GENERIC_WEALTH_GUIDANCE");
    }

    @Test
    void shouldTreatAffordabilityFollowUpAsGenericWealthGuidance() {
        var result = intentRoutingService.classifyWithRules(
                "how can I make it real",
                java.util.List.of("can I afford a $800k condo", "25% down payment")
        );

        assertThat(result.intentCode().name()).isEqualTo("WEALTH_OVERVIEW");
        assertThat(result.responsePolicy().name()).isEqualTo("GENERIC_WEALTH_GUIDANCE");
    }

    @Test
    void shouldTreatWhatShouldIDoNextAfterAffordabilityAsGenericWealthGuidance() {
        var result = intentRoutingService.classifyWithRules(
                "what should I do next",
                java.util.List.of("can I afford a $800k condo", "25% down payment")
        );

        assertThat(result.intentCode().name()).isEqualTo("WEALTH_OVERVIEW");
        assertThat(result.responsePolicy().name()).isEqualTo("GENERIC_WEALTH_GUIDANCE");
    }
}
