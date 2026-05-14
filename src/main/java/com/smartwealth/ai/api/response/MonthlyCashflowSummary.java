package com.smartwealth.ai.api.response;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record MonthlyCashflowSummary(
        YearMonth month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal netCashflow,
        BigDecimal savingsRate,
        List<CategorySpend> expenseBreakdown
) {
}
