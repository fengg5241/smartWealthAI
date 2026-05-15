package com.smartwealth.ai.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalScenarioView(
        String scenarioName,
        BigDecimal assetPrice,
        BigDecimal requiredDownPayment,
        BigDecimal currentSavingsBalance,
        BigDecimal savingsGap,
        boolean affordableNow,
        int estimatedMonthsToReachGoal,
        LocalDate estimatedReachDate,
        String currency
) {
}
