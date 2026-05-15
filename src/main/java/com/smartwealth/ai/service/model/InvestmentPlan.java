package com.smartwealth.ai.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentPlan(
        String productCode,
        String productName,
        java.math.BigDecimal annualReturnRate,
        BigDecimal monthlyInvestmentAmount,
        int savingsOnlyMonthsForSameContribution,
        int investmentMonths,
        BigDecimal estimatedInvestmentGain,
        BigDecimal estimatedTotalValueAtGoalDate,
        LocalDate estimatedReachDate,
        int savedMonthsComparedToSavingOnly
) {
}
