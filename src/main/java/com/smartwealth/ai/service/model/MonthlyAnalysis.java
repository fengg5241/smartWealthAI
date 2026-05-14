package com.smartwealth.ai.service.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

public record MonthlyAnalysis(
        YearMonth month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal netCashflow,
        BigDecimal savingsRate,
        Map<String, BigDecimal> expenseByCategory
) {
}
