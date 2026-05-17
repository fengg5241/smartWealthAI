package com.smartwealth.ai.service.model;

import com.smartwealth.ai.api.response.ChatIntentType;

public record ChatIntent(
        ChatIntentType type,
        String reason,
        WealthIntentCode intentCode,
        String intentName
) {
}
