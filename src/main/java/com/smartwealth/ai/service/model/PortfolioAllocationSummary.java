package com.smartwealth.ai.service.model;

import com.smartwealth.ai.domain.ProductCategory;
import java.math.BigDecimal;

public record PortfolioAllocationSummary(
        ProductCategory category,
        BigDecimal allocationPercent,
        BigDecimal currentValue
) {
}
