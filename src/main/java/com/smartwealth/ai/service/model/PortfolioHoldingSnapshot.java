package com.smartwealth.ai.service.model;

import com.smartwealth.ai.domain.ProductCategory;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioHoldingSnapshot(
        String productCode,
        String productName,
        ProductCategory productCategory,
        String currency,
        BigDecimal investedAmount,
        BigDecimal currentValue,
        BigDecimal allocationPercent,
        LocalDate openedAt
) {
}
