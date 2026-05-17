package com.smartwealth.ai.service.model;

public record IntentClassificationResult(
        WealthIntentCode intentCode,
        String intentName,
        String reason,
        boolean usedLlm
) {
}
