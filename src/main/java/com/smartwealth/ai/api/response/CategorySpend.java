package com.smartwealth.ai.api.response;

import java.math.BigDecimal;

public record CategorySpend(
        String category,
        BigDecimal amount,
        BigDecimal ratio
) {
}
