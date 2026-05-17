package com.smartwealth.ai.service.model;

import com.smartwealth.ai.api.response.ChatIntentType;

public record WealthWorkflow(
        ChatIntentType responseIntentType,
        WealthIntentCode intentCode,
        String intentName,
        String intentReason,
        boolean shouldRetrieveRag,
        boolean shouldRecommendProducts,
        boolean lowerRiskAlternativeOnly,
        boolean useSpecializedHandler
) {
}
