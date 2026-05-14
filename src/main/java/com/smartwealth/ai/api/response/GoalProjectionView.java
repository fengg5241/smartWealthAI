package com.smartwealth.ai.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalProjectionView(
        String goalName,
        BigDecimal targetAmount,
        LocalDate targetDate,
        BigDecimal averageMonthlySavings,
        BigDecimal projectedMonthlyContribution,
        int monthsToGoal,
        LocalDate projectedCompletionDate,
        boolean onTrack
) {
}
