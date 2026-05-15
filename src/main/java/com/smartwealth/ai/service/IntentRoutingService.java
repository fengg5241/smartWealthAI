package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.ChatIntentType;
import com.smartwealth.ai.service.model.ChatIntent;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IntentRoutingService {

    private static final List<String> WEALTH_KEYWORDS = List.of(
            "理财", "产品", "风险", "收益", "储蓄", "存钱", "目标", "收支", "消费", "投资", "基金", "资产",
            "公寓", "apartment", "首付", "房子", "买房", "购房", "支付", "支付能力", "down payment", "mortgage"
    );
    private static final List<String> WEALTH_FOLLOW_UP_KEYWORDS = List.of(
            "如何才能实现", "怎么实现", "能买吗", "首付", "风险过高", "太高", "降低风险", "更合理", "更稳健", "更低风险", "省钱"
    );
    private static final List<String> PRODUCT_REQUEST_KEYWORDS = List.of(
            "推荐产品", "什么产品", "哪个产品", "买什么", "如何实现", "怎么实现", "实现目标", "风险过高", "太高", "更合理", "更稳健", "更低风险"
    );

    public ChatIntent detect(String message, List<String> historyMessages) {
        String normalized = message == null ? "" : message.trim().toLowerCase();
        boolean wealthIntent = WEALTH_KEYWORDS.stream().anyMatch(normalized::contains);
        if (wealthIntent) {
            return new ChatIntent(ChatIntentType.WEALTH_ADVISORY, "Detected wealth-management intent from message keywords.");
        }

        String historyText = historyMessages == null ? "" : String.join(" ", historyMessages).toLowerCase();
        boolean hasWealthHistory = WEALTH_KEYWORDS.stream().anyMatch(historyText::contains)
                || historyText.contains("公寓")
                || historyText.contains("apartment")
                || historyText.contains("首付");
        boolean isFollowUp = WEALTH_FOLLOW_UP_KEYWORDS.stream().anyMatch(normalized::contains);
        if (hasWealthHistory && isFollowUp) {
            return new ChatIntent(ChatIntentType.WEALTH_ADVISORY, "Detected wealth-related follow-up intent from conversation history.");
        }

        return new ChatIntent(ChatIntentType.GENERAL_QA, "Detected general question outside wealth-advisory flow.");
    }

    public boolean shouldRecommendProducts(String message, List<String> historyMessages) {
        String normalized = message == null ? "" : message.trim().toLowerCase();
        if (PRODUCT_REQUEST_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return true;
        }
        String historyText = historyMessages == null ? "" : String.join(" ", historyMessages).toLowerCase();
        return historyText.contains("如何实现") || historyText.contains("怎么实现");
    }
}
