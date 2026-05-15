package com.smartwealth.ai.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalScenarioAnalysis(
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
