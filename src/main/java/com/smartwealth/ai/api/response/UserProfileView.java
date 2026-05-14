package com.smartwealth.ai.api.response;

import com.smartwealth.ai.domain.RiskLevel;

public record UserProfileView(
        Long userId,
        String fullName,
        RiskLevel riskLevel
) {
}
