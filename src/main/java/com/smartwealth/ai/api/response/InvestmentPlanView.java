package com.smartwealth.ai.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentPlanView(
        String productCode,
        String productName,
        java.math.BigDecimal annualReturnRate,
        BigDecimal monthlyInvestmentAmount,
        int savingsOnlyMonthsForSameContribution,
        int investmentMonths,
        BigDecimal estimatedInvestmentGain,
        BigDecimal estimatedTotalValueAtGoalDate,
        LocalDate estimatedReachDate,
        int savedMonthsComparedToSavingOnly,
        int timeSavedPercent
) {
}
