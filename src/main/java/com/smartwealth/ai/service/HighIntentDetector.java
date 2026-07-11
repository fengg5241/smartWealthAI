package com.smartwealth.ai.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class HighIntentDetector {

    // Chinese high-intent keywords
    private static final List<String> CN_KEYWORDS = List.of(
            "我要看房", "我想看房", "我要买房", "我要租房", "我在找房",
            "我想买保险", "我要买保险", "我想了解保险", "我要咨询保险",
            "多少保费", "多少钱", "价格是多少", "怎么收费", "报价",
            "立刻", "马上", "尽快", "着急", "紧急",
            "电话", "打给我", "联系我", "怎么联系", "微信",
            "现在方便", "见面", "约看房", "看房时间", "预约",
            "我想订", "我要订", "我想预约", "我要预约"
    );

    // English high-intent keywords
    private static final List<String> EN_KEYWORDS = List.of(
            "i want to view", "i want to buy", "i want to rent", "i'm looking for",
            "i want insurance", "i need insurance", "quote", "price", "how much",
            "immediately", "asap", "urgent", "call me", "contact me",
            "whatsapp me", "my number is", "book", "schedule", "appointment",
            "i'm interested", "i want to order", "i'd like to order"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\+?\\d{8,15}|\\d{3,4}[-.]?\\d{3,4}[-.]?\\d{3,4}");

    public boolean isHighIntent(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase();

        // Check for phone numbers shared voluntarily
        if (PHONE_PATTERN.matcher(message).find()) {
            return true;
        }

        // Check Chinese keywords
        for (String kw : CN_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }

        // Check English keywords
        for (String kw : EN_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }

        return false;
    }
}
