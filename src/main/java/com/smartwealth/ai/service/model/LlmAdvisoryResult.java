package com.smartwealth.ai.service.model;

import java.util.List;

public record LlmAdvisoryResult(
        List<LlmProductSelection> finalRecommendations,
        String selectionSummary,
        String answer
) {
}
