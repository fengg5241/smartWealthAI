package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.ChatIntentType;
import com.smartwealth.ai.service.model.ChatIntent;
import com.smartwealth.ai.service.model.IntentClassificationResult;
import com.smartwealth.ai.service.model.ResponsePolicy;
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
            "推荐产品", "理财产品推荐", "什么产品", "哪个产品", "买什么", "如何实现", "怎么实现", "实现目标", "风险过高", "太高", "更合理", "更稳健", "更低风险"
    );
    private static final List<String> CN_GENERIC_GUIDANCE_PHRASES = List.of(
            "投资建议", "理财建议", "理财推荐", "怎么理财", "如何理财", "投资入门", "怎么开始投资", "如何开始投资"
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
            "can i afford", "afford", "down payment", "mortgage", "is it enough", "do i have enough",
            "condo", "house", "home", "property", "can i buy"
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
            "risk level", "asset allocation", "savings plan", "diversify", "portfolio", "allocation",
            "investment advice", "financial advice", "investing advice", "investment strategy",
            "how to start investing", "start investing", "begin investing", "first time investing"
    );
    private static final List<String> EN_CLARIFY_PHRASES = List.of(
            "retirement or pay off debt", "pay off debt", "retirement"
    );
    private static final List<String> EN_FOLLOW_UP_GUIDANCE_PHRASES = List.of(
            "how can i make it real", "how do i make it real", "what should i do next", "how can i get there",
            "how do i get there", "what can i do to make it happen"
    );
    private static final List<String> EN_SAFE_DECLINE_PHRASES = List.of(
            "tax-efficient", "tax efficient", "insurance", "good time to buy stocks", "buy stocks now", "market timing"
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
                classification.intentName(),
                classification.responsePolicy()
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
        boolean hasAffordabilityHistory = historyText.contains("afford")
                || historyText.contains("down payment")
                || historyText.contains("condo")
                || historyText.contains("house")
                || historyText.contains("property")
                || historyText.contains("买房")
                || historyText.contains("首付");

        if (hasAffordabilityHistory && containsPhrase(normalized, EN_FOLLOW_UP_GUIDANCE_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.WEALTH_OVERVIEW,
                    "Wealth Overview",
                    "Rule-based classifier detected a follow-up wealth-guidance question after an affordability discussion.",
                    false,
                    ResponsePolicy.GENERIC_WEALTH_GUIDANCE
            );
        }

        if (containsPhrase(normalized, EN_PRODUCT_COMPARISON_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.PRODUCT_COMPARISON,
                    "Product Comparison",
                    "Rule-based classifier detected a product comparison request.",
                    false,
                    ResponsePolicy.SPECIALIZED_EXECUTE
            );
        }

        if (containsAny(normalized, "can i afford", "afford", "is it enough", "do i have enough", "can i buy")
                || containsPhrase(normalized, EN_GOAL_FEASIBILITY_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.GOAL_FEASIBILITY,
                    "Goal Feasibility",
                    "Rule-based classifier detected an affordability or purchase-feasibility request.",
                    false,
                    ResponsePolicy.GENERIC_WEALTH_GUIDANCE
            );
        }

        if (containsPhrase(normalized, EN_FUND_SELECTION_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.FUND_SELECTION,
                    "Fund Selection",
                    "Rule-based classifier detected a fund selection request with a stated budget.",
                    false,
                    ResponsePolicy.SPECIALIZED_EXECUTE
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
                    false,
                    normalized.contains("portfolio") || normalized.contains("volatile")
                            ? ResponsePolicy.SPECIALIZED_EXECUTE
                            : ResponsePolicy.GENERIC_WEALTH_GUIDANCE
            );
        }

        if (containsPhrase(normalized, EN_SAFE_DECLINE_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.WEALTH_OVERVIEW,
                    "Wealth Overview",
                    "Rule-based classifier detected a wealth-related request that requires capabilities outside the current safe support boundary.",
                    false,
                    ResponsePolicy.SAFE_DECLINE
            );
        }

        if (containsPhrase(normalized, EN_CLARIFY_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.WEALTH_OVERVIEW,
                    "Wealth Overview",
                    "Rule-based classifier detected a wealth-related request that needs more user-specific data before answering well.",
                    false,
                    ResponsePolicy.ASK_CLARIFY
            );
        }

        if (PRODUCT_REQUEST_KEYWORDS.stream().anyMatch(normalized::contains)
                || containsPhrase(normalized, EN_PRODUCT_RECOMMENDATION_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.PRODUCT_RECOMMENDATION,
                    "Product Recommendation",
                    "Rule-based classifier detected a product recommendation request.",
                    false,
                    ResponsePolicy.SPECIALIZED_EXECUTE
            );
        }

        if (containsAny(normalized, "目标", "达成", "完成", "何时", "多久")
                || containsPhrase(normalized, EN_GOAL_PROGRESS_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.GOAL_PROGRESS,
                    "Goal Progress",
                    "Rule-based classifier detected a savings-goal progress request.",
                    false,
                    ResponsePolicy.GENERIC_WEALTH_GUIDANCE
            );
        }

        if (containsAny(normalized, "收支", "消费", "支出", "收入")
                || containsPhrase(normalized, EN_CASHFLOW_ANALYSIS_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.CASHFLOW_ANALYSIS,
                    "Cashflow Analysis",
                    "Rule-based classifier detected a cashflow analysis request.",
                    false,
                    ResponsePolicy.GENERIC_WEALTH_GUIDANCE
            );
        }

        if (isGenericAdviceRequest(normalized)) {
            return new IntentClassificationResult(
                    WealthIntentCode.WEALTH_OVERVIEW,
                    "Wealth Overview",
                    "Rule-based classifier detected a general wealth-guidance request.",
                    false,
                    ResponsePolicy.GENERIC_WEALTH_GUIDANCE
            );
        }

        if (wealthIntent || hasWealthHistory || containsPhrase(normalized, EN_WEALTH_OVERVIEW_PHRASES)) {
            return new IntentClassificationResult(
                    WealthIntentCode.WEALTH_OVERVIEW,
                    "Wealth Overview",
                    "Rule-based classifier detected a general wealth-advisory request.",
                    false,
                    ResponsePolicy.GENERIC_WEALTH_GUIDANCE
            );
        }

        if (isLikelyOutOfScope(normalized)) {
            return new IntentClassificationResult(
                    WealthIntentCode.OUT_OF_SCOPE,
                    "Out of Scope",
                    "Rule-based classifier detected a question outside wealth-advisory scope.",
                    false,
                    ResponsePolicy.SAFE_DECLINE
            );
        }

        return new IntentClassificationResult(
                WealthIntentCode.OUT_OF_SCOPE,
                "Out of Scope",
                "Rule-based classifier detected a general question outside wealth-advisory flow.",
                false,
                ResponsePolicy.SAFE_DECLINE
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

    public boolean isGenericAdviceRequest(String message) {
        String normalized = normalize(message);
        return containsPhrase(normalized, CN_GENERIC_GUIDANCE_PHRASES)
                || containsPhrase(normalized, EN_WEALTH_OVERVIEW_PHRASES);
    }

    public boolean isFollowUpGuidanceRequest(String message) {
        String normalized = normalize(message);
        return containsPhrase(normalized, EN_FOLLOW_UP_GUIDANCE_PHRASES);
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
