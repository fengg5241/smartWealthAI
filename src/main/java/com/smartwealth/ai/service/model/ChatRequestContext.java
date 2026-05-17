package com.smartwealth.ai.service.model;

public record ChatRequestContext(
        SupportedLanguage language,
        IntentClassificationResult classification
) {
}
