package com.smartwealth.ai.service.model;

import java.util.Map;

public record RagSnippet(
        String id,
        String text,
        Map<String, Object> metadata
) {
}
