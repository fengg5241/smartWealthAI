package com.smartwealth.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwealth.ai.config.WealthAdvisorProperties;
import com.smartwealth.ai.service.model.IntentClassificationResult;
import com.smartwealth.ai.service.model.ResponsePolicy;
import com.smartwealth.ai.service.model.SupportedLanguage;
import com.smartwealth.ai.service.model.WealthIntentCode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class IntentClassificationService {

    private static final Logger log = LoggerFactory.getLogger(IntentClassificationService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final IntentRoutingService intentRoutingService;
    private final WealthAdvisorProperties properties;

    public IntentClassificationService(
            ChatClient chatClient,
            ObjectMapper objectMapper,
            IntentRoutingService intentRoutingService,
            WealthAdvisorProperties properties
    ) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.intentRoutingService = intentRoutingService;
        this.properties = properties;
    }

    public IntentClassificationResult classify(String message, List<String> historyMessages, SupportedLanguage language) {
        IntentClassificationResult fallback = intentRoutingService.classifyWithRules(message, historyMessages);
        if (shouldPreferRuleResult(message, fallback)) {
            return fallback;
        }
        try {
            String raw = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(properties.getLlm().getClassificationModel())
                            .temperature(properties.getLlm().getClassificationTemperature())
                            .maxTokens(properties.getLlm().getClassificationMaxTokens())
                            .build())
                    .system(buildSystemPrompt(language))
                    .user(buildUserPrompt(message, historyMessages, language))
                    .call()
                    .content();

            if (raw == null || raw.isBlank()) {
                return fallback;
            }

            ParsedClassification parsed = objectMapper.readValue(stripCodeFence(raw), ParsedClassification.class);
            WealthIntentCode intentCode = safeIntentCode(parsed.intentCode());
            if (intentCode == null) {
                return fallback;
            }
            IntentClassificationResult llmResult = new IntentClassificationResult(
                    intentCode,
                    parsed.intentName() == null || parsed.intentName().isBlank() ? toName(intentCode) : parsed.intentName().trim(),
                    parsed.reason() == null || parsed.reason().isBlank() ? "LLM classifier returned no reason." : parsed.reason().trim(),
                    true,
                    safeResponsePolicy(parsed.responsePolicy(), intentCode)
            );
            return shouldOverrideLlmWithRule(message, fallback, llmResult) ? fallback : llmResult;
        } catch (Exception exception) {
            log.warn("Intent classification failed, falling back to rules: {}", exception.getMessage());
            return fallback;
        }
    }

    private String buildSystemPrompt(SupportedLanguage language) {
        String instruction = language == SupportedLanguage.EN
                ? "You are a wealth-advisory intent classifier. Classify only. Do not answer the user."
                : "你是财富管理问答的意图分类器。只做分类，不回答用户问题。";
        return """
                %s
                You must output JSON only.
                Allowed intent codes:
                - OUT_OF_SCOPE
                - WEALTH_OVERVIEW
                - CASHFLOW_ANALYSIS
                - GOAL_PROGRESS
                - GOAL_FEASIBILITY
                - PRODUCT_RECOMMENDATION
                - RISK_REBALANCING
                - FUND_SELECTION
                - PRODUCT_COMPARISON
                - PORTFOLIO_REBALANCING

                Allowed response policies:
                - SPECIALIZED_EXECUTE
                - GENERIC_WEALTH_GUIDANCE
                - ASK_CLARIFY
                - SAFE_DECLINE

                Classification rules:
                - Use FUND_SELECTION when the user asks for the best fund(s) for a stated budget.
                - Use PRODUCT_COMPARISON when the user asks to compare fixed deposits and bonds or compare two product types.
                - Use PORTFOLIO_REBALANCING when the user asks how to adjust an existing portfolio in a volatile market.
                - Use PRODUCT_RECOMMENDATION only when the user explicitly asks for products, investment options, what to buy, or how to invest.
                - Use WEALTH_OVERVIEW with GENERIC_WEALTH_GUIDANCE for high-level educational investment questions such as how to start investing, general investment advice, diversification, or risk-level tradeoffs, unless the user explicitly asks for specific products.
                - Use RISK_REBALANCING only when the user explicitly asks to lower risk or says the current plan is too risky.
                - Use GOAL_FEASIBILITY for affordability, down payment sufficiency, or whether current savings are enough.
                - Use GOAL_PROGRESS for timeline-to-goal, target progress, or how long it takes to reach a goal.
                - Use CASHFLOW_ANALYSIS for income, expense, spending, budget, or cashflow analysis.
                - Use WEALTH_OVERVIEW for general financial overview, risk profile, or broad wealth-management questions without an explicit request for products.
                - Use GENERIC_WEALTH_GUIDANCE for wealth-management questions that are within scope but do not require a specialized deterministic handler.
                - Use ASK_CLARIFY when the question is wealth-related but more user-specific data is needed to answer well.
                - Use SAFE_DECLINE when the question is wealth-related but the current system should not answer due to capability or safety boundaries, such as tax planning, insurance recommendation, or market timing.
                - Do not infer PRODUCT_RECOMMENDATION just because the conversation history previously mentioned products.
                - Focus primarily on the current user message, and use history only to resolve short follow-up questions.

                JSON format:
                {
                  "intentCode": "固定意图编码",
                  "intentName": "固定意图名称",
                  "reason": "简短分类原因",
                  "responsePolicy": "固定回复策略"
                }
                """.formatted(instruction);
    }

    private String buildUserPrompt(String message, List<String> historyMessages, SupportedLanguage language) {
        String history = historyMessages == null || historyMessages.isEmpty()
                ? ""
                : String.join("\n", historyMessages);
        return """
                language=%s
                history:
                %s

                user_message:
                %s
                """.formatted(language.name(), history, message == null ? "" : message);
    }

    private WealthIntentCode safeIntentCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return WealthIntentCode.valueOf(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ResponsePolicy safeResponsePolicy(String raw, WealthIntentCode intentCode) {
        if (raw != null && !raw.isBlank()) {
            try {
                return ResponsePolicy.valueOf(raw.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return defaultPolicy(intentCode);
    }

    private ResponsePolicy defaultPolicy(WealthIntentCode intentCode) {
        return switch (intentCode) {
            case FUND_SELECTION, PRODUCT_COMPARISON, PORTFOLIO_REBALANCING -> ResponsePolicy.SPECIALIZED_EXECUTE;
            case OUT_OF_SCOPE -> ResponsePolicy.SAFE_DECLINE;
            default -> ResponsePolicy.GENERIC_WEALTH_GUIDANCE;
        };
    }

    private boolean shouldPreferRuleResult(String message, IntentClassificationResult ruleResult) {
        return ruleResult.responsePolicy() == ResponsePolicy.SPECIALIZED_EXECUTE
                || ruleResult.intentCode() == WealthIntentCode.GOAL_FEASIBILITY
                || intentRoutingService.isGenericAdviceRequest(message)
                || intentRoutingService.isFollowUpGuidanceRequest(message);
    }

    private boolean shouldOverrideLlmWithRule(
            String message,
            IntentClassificationResult ruleResult,
            IntentClassificationResult llmResult
    ) {
        if (shouldPreferRuleResult(message, ruleResult)) {
            return true;
        }
        return llmResult.intentCode() == WealthIntentCode.OUT_OF_SCOPE
                && ruleResult.intentCode() != WealthIntentCode.OUT_OF_SCOPE;
    }

    private String toName(WealthIntentCode intentCode) {
        return switch (intentCode) {
            case OUT_OF_SCOPE -> "Out of Scope";
            case WEALTH_OVERVIEW -> "Wealth Overview";
            case CASHFLOW_ANALYSIS -> "Cashflow Analysis";
            case GOAL_PROGRESS -> "Goal Progress";
            case GOAL_FEASIBILITY -> "Goal Feasibility";
            case PRODUCT_RECOMMENDATION -> "Product Recommendation";
            case RISK_REBALANCING -> "Risk Rebalancing";
            case FUND_SELECTION -> "Fund Selection";
            case PRODUCT_COMPARISON -> "Product Comparison";
            case PORTFOLIO_REBALANCING -> "Portfolio Rebalancing";
        };
    }

    private String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private record ParsedClassification(
            String intentCode,
            String intentName,
            String reason,
            String responsePolicy
    ) {
    }
}
