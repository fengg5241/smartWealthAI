package com.smartwealth.ai.service.model;

import com.smartwealth.ai.domain.FinancialProduct;

public record ProductRecommendation(
        FinancialProduct product,
        String reason,
        boolean lowerRiskAlternative
) {
}
