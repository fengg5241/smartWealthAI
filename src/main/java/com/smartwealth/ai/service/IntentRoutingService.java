package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.ChatIntentType;
import com.smartwealth.ai.service.model.ChatIntent;
import com.smartwealth.ai.service.model.IntentClassificationResult;
import com.smartwealth.ai.service.model.SupportedLanguage;
import com.smartwealth.ai.service.model.WealthIntentCode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class IntentRoutingService {

    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}");
    private static final Pattern LATIN_PATTERN = Pattern.compile("[A-Za-z]");
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
    private static final List<String> EN_PRODUCT_RECOMMENDATION_PHRASES = List.of(
            "recommend a product", "recommend products", "product recommendation", "which product",
            "what product", "what should i buy", "what should i invest in", "investment product",
            "portfolio recommendation", "show me products", "suitable product", "suitable products",
            "best product", "best products", "recommend a suitable product"
    );
    private static final List<String> EN_FUND_SELECTION_PHRASES = List.of(
            "best fund", "best funds", "which fund", "which funds", "fund for", "funds for", "50,000"
    );
    private static final List<String> EN_PRODUCT_COMPARISON_PHRASES = List.of(
            "fixed deposit or bond", "fixed deposits or bonds", "deposit or bond", "fd or bond", "which is better for me"
    );
    private static final List<String> EN_RISK_REBALANCING_PHRASES = List.of(
            "too risky", "lower risk", "less risky", "reduce risk", "more conservative", "rebalance risk",
            "adjust my portfolio", "volatile market", "market is so volatile", "adjust my allocation"
    );
    private static final List<String> EN_GOAL_FEASIBILITY_PHRASES = List.of(
            "can i afford", "afford", "down payment", "mortgage", "is it enough", "do i have enough"
    );
    private static final List<String> EN_GOAL_PROGRESS_PHRASES = List.of(
            "goal progress", "reach my goal", "reach the goal", "when can i reach", "how long to reach",
            "savings goal", "target amount", "goal timeline"
    );
    private static final List<String> EN_CASHFLOW_ANALYSIS_PHRASES = List.of(
            "cashflow", "cash flow", "expense analysis", "income analysis", "spending analysis",
            "budget analysis", "analyze my spending", "analyze my expenses"
    );
    private static final List<String> EN_WEALTH_OVERVIEW_PHRASES = List.of(
            "wealth overview", "financial overview", "my finances", "my financial situation",
            "risk level", "asset allocation", "savings plan"
    );

    public SupportedLanguage detectLanguage(String message) {
        if (message == null || message.isBlank()) {
            return SupportedLanguage.ZH;
        }
        if (isEnglishInput(message)) {
            return SupportedLanguage.EN;
        }
        return SupportedLanguage.ZH;
    }

    public ChatIntent detect(String message, List<String> historyMessages) {
        IntentClassificationResult classification = classifyWithRules(message, historyMessages);
        ChatIntentType intentType = classification.intentCode() == WealthIntentCode.OUT_OF_SCOPE
                ? ChatIntentType.GENERAL_QA
                : ChatIntentType.WEALTH_ADVISORY;
        return new ChatIntent(
                intentType,
                classification.reason(),
                classification.intentCode(),
                classification.intentName()
        );
    }

    public IntentClassificationResult classifyWithRules(String message, List<String> historyMessages) {
        String normalized = normalize(message);
        String historyText = normalize(historyMessages == null ? "" : String.join(" ", historyMessages));

        boolean wealthIntent = WEALTH_KEYWORDS.stream().anyMatch(normalized::contains);
        boolean hasWealthHistory = WEALTH_KEYWORDS.stream().anyMatch(historyText::contains)
                || historyText.contains("公寓")
                || historyText.contains("apartment")
                || historyText.contains("首付");

        if (containsPhrase(normalized, EN_PRODUCT_COMPARISON_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.PRODUCT_COMPARISON,
                    "Product Comparison",
                    "Rule-based classifier detected a product comparison request.",
                    false
            );
        }

        if (containsPhrase(normalized, EN_FUND_SELECTION_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.FUND_SELECTION,
                    "Fund Selection",
                    "Rule-based classifier detected a fund selection request with a stated budget.",
                    false
            );
        }

        if (containsAny(normalized, "风险过高", "稳健", "降低风险") || containsPhrase(normalized, EN_RISK_REBALANCING_PHRASES)) {
            return new IntentClassificationResult(
                    normalized.contains("portfolio") || normalized.contains("volatile")
                            ? WealthIntentCode.PORTFOLIO_REBALANCING
                            : WealthIntentCode.RISK_REBALANCING,
                    normalized.contains("portfolio") || normalized.contains("volatile")
                            ? "Portfolio Rebalancing"
                            : "Risk Rebalancing",
                    "Rule-based classifier detected a request to lower risk or rebalance a volatile portfolio.",
                    false
            );
        }

        if (containsAny(normalized, "推荐", "推荐产品", "什么产品", "哪个产品", "买什么")
                || containsPhrase(normalized, EN_PRODUCT_RECOMMENDATION_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.PRODUCT_RECOMMENDATION,
                    "Product Recommendation",
                    "Rule-based classifier detected a product recommendation request.",
                    false
            );
        }

        if (containsAny(normalized, "够不够", "能不能", "能否支付", "支付得起", "首付")
                || containsPhrase(normalized, EN_GOAL_FEASIBILITY_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.GOAL_FEASIBILITY,
                    "Goal Feasibility",
                    "Rule-based classifier detected a payment-feasibility or goal-affordability request.",
                    false
            );
        }

        if (containsAny(normalized, "目标", "达成", "完成", "何时", "多久")
                || containsPhrase(normalized, EN_GOAL_PROGRESS_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.GOAL_PROGRESS,
                    "Goal Progress",
                    "Rule-based classifier detected a savings-goal progress request.",
                    false
            );
        }

        if (containsAny(normalized, "收支", "消费", "支出", "收入")
                || containsPhrase(normalized, EN_CASHFLOW_ANALYSIS_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.CASHFLOW_ANALYSIS,
                    "Cashflow Analysis",
                    "Rule-based classifier detected a cashflow analysis request.",
                    false
            );
        }

        if (wealthIntent || hasWealthHistory || containsPhrase(normalized, EN_WEALTH_OVERVIEW_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.WEALTH_OVERVIEW,
                    "Wealth Overview",
                    "Rule-based classifier detected a general wealth-advisory request.",
                    false
            );
        }

        if (isLikelyOutOfScope(normalized)) {
            return new IntentClassificationResult(
                    WealthIntentCode.OUT_OF_SCOPE,
                    "Out of Scope",
                    "Rule-based classifier detected a question outside wealth-advisory scope.",
                    false
            );
        }

        return new IntentClassificationResult(
                WealthIntentCode.OUT_OF_SCOPE,
                "Out of Scope",
                "Rule-based classifier detected a general question outside wealth-advisory flow.",
                false
        );
    }

    public boolean shouldRecommendProducts(String message, List<String> historyMessages) {
        String normalized = normalize(message);
        if (PRODUCT_REQUEST_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return true;
        }
        String historyText = normalize(historyMessages == null ? "" : String.join(" ", historyMessages));
        return historyText.contains("如何实现") || historyText.contains("怎么实现");
    }

    boolean isEnglishInput(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return LATIN_PATTERN.matcher(message).find() && !HAN_PATTERN.matcher(message).find();
    }

    private boolean isLikelyOutOfScope(String normalized) {
        return normalized.isBlank() || !WEALTH_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPhrase(String text, List<String> phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
    }
}
