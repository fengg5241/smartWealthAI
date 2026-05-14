package com.smartwealth.ai.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalProjection(
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
