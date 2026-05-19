package com.smartwealth.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwealth.ai.config.WealthAdvisorProperties;
import com.smartwealth.ai.service.model.LlmAdvisoryResult;
import com.smartwealth.ai.service.model.LlmProductSelection;
import com.smartwealth.ai.service.model.ProductRecommendation;
import com.smartwealth.ai.service.model.ResponsePolicy;
import com.smartwealth.ai.service.model.SpecializedAdvisoryResult;
import com.smartwealth.ai.service.model.SupportedLanguage;
import com.smartwealth.ai.service.model.WealthInsight;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class LlmAdvisoryService {

    private static final Logger log = LoggerFactory.getLogger(LlmAdvisoryService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AdvisoryNarrativeService advisoryNarrativeService;
    private final WealthAdvisorProperties properties;

    public LlmAdvisoryService(
            ChatClient chatClient,
            ObjectMapper objectMapper,
            AdvisoryNarrativeService advisoryNarrativeService,
            WealthAdvisorProperties properties
    ) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.advisoryNarrativeService = advisoryNarrativeService;
        this.properties = properties;
    }

    public LlmAdvisoryResult advise(WealthInsight insight, String userMessage) {
        LlmAdvisoryResult fallback = buildFallback(insight, userMessage);
        log.info("Calling LLM for userId={}, candidates={}, ragSnippets={}",
                insight.userId(),
                insight.productRecommendations().size(),
                insight.ragContextSnippets().size());
        String systemPrompt = buildSystemPrompt(insight.language());

        String userPrompt = buildUserPrompt(insight, userMessage);

        try {
            String raw = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(properties.getLlm().getAnswerModel())
                            .temperature(properties.getLlm().getAnswerTemperature())
                            .maxTokens(properties.getLlm().getAnswerMaxTokens())
                            .build())
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            if (raw == null || raw.isBlank()) {
                log.warn("LLM returned empty content for userId={}, using fallback", insight.userId());
                return fallback;
            }

            ParsedLlmResponse parsed = objectMapper.readValue(stripCodeFence(raw), ParsedLlmResponse.class);
            Map<String, ProductRecommendation> candidates = insight.productRecommendations().stream()
                    .collect(Collectors.toMap(item -> item.product().getProductCode(), Function.identity()));

            List<LlmProductSelection> validatedSelections = parsed.finalRecommendations() == null
                    ? List.of()
                    : parsed.finalRecommendations().stream()
                    .filter(item -> item.productCode() != null && candidates.containsKey(item.productCode()))
                    .map(item -> new LlmProductSelection(candidates.get(item.productCode()), safeText(item.reason())))
                    .toList();

            if (validatedSelections.isEmpty()) {
                if (!insight.recommendProducts()) {
                    log.info("LLM completed for userId={} without product recommendation as requested", insight.userId());
                    return new LlmAdvisoryResult(List.of(), safeText(parsed.selectionSummary()), safeText(parsed.answer()));
                }
                log.warn("LLM response did not contain valid candidate product codes for userId={}, using fallback", insight.userId());
                return fallback;
            }

            log.info("LLM completed for userId={}, finalRecommendations={}", insight.userId(), validatedSelections.size());
            String structuredAnswer = advisoryNarrativeService.buildStructuredRecommendationAnswer(
                    insight,
                    validatedSelections
            );
            return new LlmAdvisoryResult(
                    validatedSelections,
                    safeText(parsed.selectionSummary()),
                    structuredAnswer
            );
        } catch (Exception exception) {
            log.warn("LLM call failed for userId={}, using fallback: {}", insight.userId(), exception.getMessage());
            return fallback;
        }
    }

    public SpecializedAdvisoryResult polishSpecializedAnswer(WealthInsight insight, SpecializedAdvisoryResult baseResult, String userMessage) {
        try {
            String raw = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(properties.getLlm().getAnswerModel())
                            .temperature(properties.getLlm().getAnswerTemperature())
                            .maxTokens(properties.getLlm().getAnswerMaxTokens())
                            .build())
                    .system(buildSpecializedPolishSystemPrompt(insight.language()))
                    .user(buildSpecializedPolishUserPrompt(insight, baseResult, userMessage))
                    .call()
                    .content();
            if (raw == null || raw.isBlank()) {
                return baseResult;
            }

            ParsedPolishResponse parsed = objectMapper.readValue(stripCodeFence(raw), ParsedPolishResponse.class);
            String polishedAnswer = safeText(parsed.answer());
            String polishedSummary = safeText(parsed.selectionSummary());
            return new SpecializedAdvisoryResult(
                    baseResult.candidateProducts(),
                    baseResult.finalRecommendations(),
                    baseResult.investmentPlans(),
                    baseResult.investmentPlanSummaries(),
                    baseResult.advisoryHighlights(),
                    polishedSummary.isBlank() ? baseResult.selectionSummary() : polishedSummary,
                    polishedAnswer.isBlank() ? baseResult.answer() : polishedAnswer
            );
        } catch (Exception exception) {
            log.warn("Specialized answer polish failed for userId={}, using base result: {}", insight.userId(), exception.getMessage());
            return baseResult;
        }
    }

    public String generateGenericWealthGuidance(WealthInsight insight, String userMessage) {
        String fallback = buildGenericFallback(insight, userMessage);
        try {
            String raw = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(properties.getLlm().getAnswerModel())
                            .temperature(properties.getLlm().getAnswerTemperature())
                            .maxTokens(properties.getLlm().getAnswerMaxTokens())
                            .build())
                    .system(buildGenericGuidanceSystemPrompt(insight.language()))
                    .user(buildGenericGuidanceUserPrompt(insight, userMessage))
                    .call()
                    .content();
            return raw == null || raw.isBlank() ? fallback : raw.trim();
        } catch (Exception exception) {
            log.warn("Generic wealth guidance generation failed for userId={}, using fallback: {}", insight.userId(), exception.getMessage());
            return fallback;
        }
    }

    private LlmAdvisoryResult buildFallback(WealthInsight insight, String userMessage) {
        List<LlmProductSelection> selections = insight.productRecommendations().stream()
                .limit(1)
                .map(item -> new LlmProductSelection(item, item.reason()))
                .toList();

        String answer = advisoryNarrativeService.buildFallbackAnswer(
                insight.userId(),
                insight.language(),
                insight.monthlyAnalyses(),
                insight.goalProjection(),
                insight.goalScenarioAnalysis(),
                insight.productRecommendations(),
                insight.investmentPlans(),
                insight.advisoryHighlights(),
                insight.ragContextSnippets(),
                userMessage
        );

        String summary = buildFallbackSummary(insight.language(), selections.isEmpty());

        return new LlmAdvisoryResult(selections, summary, answer);
    }

    private String buildSystemPrompt(SupportedLanguage language) {
        if (language == SupportedLanguage.EN) {
            return """
                    You are an AI wealth advisor.
                    Base your final answer only on the provided cashflow analysis, savings goal projection, risk level, RAG context, candidate products, and investment plans.
                    If the current question is only about affordability or whether the target is enough, and the user did not ask for products or an implementation path, do not proactively recommend products.
                    Recommend products only when the workflow explicitly allows product recommendation.
                    If you recommend products, you may only choose from the provided candidate products.
                    Your answer must include risk and compliance reminders, numeric support, and an explanation tied to the savings goal.
                    If you recommend products, include product name, code, annualized return, minimum holding period, liquidity, and risk/compliance notes.
                    If you recommend products, compare at least two investment plans with monthly amount, time to goal, expected gain, expected reach date, and time saved versus pure saving.
                    Output JSON only, with no markdown and no extra commentary.
                    JSON:
                    {
                      "selectionSummary": "one-line summary",
                      "finalRecommendations": [
                        {"productCode": "candidate code only", "reason": "why recommended"}
                      ],
                      "answer": "final user-facing answer in English"
                    }
                    """;
        }
        return """
                你是财富管理领域的 AI 财富顾问。
                你必须基于给定的用户收支分析、储蓄目标、风险等级、RAG 检索上下文和候选理财产品做最终推荐。
                如果当前问题只是判断“现在能否支付/是否足够”，且没有主动要求产品方案或实现路径，则不要主动推荐产品。
                只有工作流明确允许推荐产品时，才给出产品推荐。
                一旦推荐产品，你只能从候选理财产品中选择最终推荐产品，严禁输出候选列表之外的产品编码。
                你的回答必须体现合规性，明确说明风险提示，并结合储蓄目标解释推荐原因。
                你的回答必须明确给出数据支持，至少包含：当前存款、目标首付、资金缺口、当前月度可投入金额、预计几个月达到目标。
                估算月份时，只能依据已提供的场景测算结果，不要自行缩短月份。
                如果推荐产品，回答必须明确给出产品细节，至少包含：产品名称、产品编码、年化收益率、最短持有期、流动性、风险/合规提示。
                如果推荐产品，回答还必须明确比较至少两档投资计划：不同每月购买金额、对应购买月数、预计收益、预计何时达到目标、比单纯储蓄节约多少时间。
                你必须只输出 JSON，不要输出 markdown，不要输出额外说明。
                JSON 结构如下：
                {
                  "selectionSummary": "一句话总结最终推荐策略",
                  "finalRecommendations": [
                    {"productCode": "产品编码", "reason": "推荐原因"}
                  ],
                  "answer": "面向用户的最终自然语言回答"
                }
                """;
    }

    private String buildGenericGuidanceSystemPrompt(SupportedLanguage language) {
        if (language == SupportedLanguage.EN) {
            return """
                    You are a wealth-management advisor answering a general in-scope wealth question.
                    Do not force product recommendations unless the user explicitly asks for products.
                    Answer with a direct core judgement first, then explain the main factors, then connect them to the user's known risk level, cashflow, savings goal, or portfolio when relevant.
                    If user data is insufficient for a precise answer, say what extra information would improve the answer.
                    If the intent is GOAL_FEASIBILITY, you must follow the provided scenario calculation exactly and clearly answer yes, no, or not yet.
                    For GOAL_FEASIBILITY, do not use vague language such as "may be able to afford" when the provided gap is greater than zero.
                    If scenario.affordableNow is false, you must state that the user cannot afford it yet under the provided assumption.
                    If scenario.affordableNow is true, you must state that the user can afford it under the provided assumption.
                    Keep the answer practical and concise.
                    """;
        }
        return """
                你是财富管理顾问，正在回答一个处于支持范围内、但不一定需要专属处理器的通用理财问题。
                除非用户明确索要产品，否则不要强行推荐产品。
                回答时先给出核心判断，再解释主要影响因素，再结合用户已知的风险等级、现金流、储蓄目标或持仓情况做个性化说明。
                如果当前数据不足以给出更精确结论，要明确说明还需要什么信息。
                如果意图是 GOAL_FEASIBILITY，你必须严格依据给定的场景测算结果回答“可以买 / 还不行 / 还买不起”。
                对于 GOAL_FEASIBILITY，只要 scenario.affordableNow 为 false，就不能使用“可能可以买得起”这类模糊表达。
                只要 scenario.affordableNow 为 true，才可以明确说当前买得起。
                保持回答务实、简洁、可执行。
                """;
    }

    private String buildGenericGuidanceUserPrompt(WealthInsight insight, String userMessage) {
        return """
                user_question:
                %s

                response_policy:
                %s

                intent:
                %s

                risk_level:
                %s

                monthly_cashflow:
                %s

                goal_projection:
                %s

                goal_scenario:
                %s

                portfolio_holdings:
                %s

                advisory_highlights:
                %s
                """.formatted(
                userMessage,
                insight.workflow().responsePolicy(),
                insight.workflow().intentCode(),
                insight.riskLevel(),
                insight.monthlyAnalyses(),
                insight.goalProjection(),
                insight.goalScenarioAnalysis(),
                insight.portfolioHoldings(),
                insight.advisoryHighlights()
        );
    }

    private String buildSpecializedPolishSystemPrompt(SupportedLanguage language) {
        if (language == SupportedLanguage.EN) {
            return """
                    You are an AI editor polishing a structured wealth-management answer.
                    Rewrite only for clarity, flow, and readability.
                    Do not change any numbers, product codes, product names, allocation amounts, percentages, currencies, or conclusions.
                    Do not add new products, new recommendations, or new claims.
                    Keep the answer grounded strictly in the provided base result.
                    Output JSON only:
                    {
                      "selectionSummary": "polished one-line summary",
                      "answer": "polished final answer in English"
                    }
                    """;
        }
        return """
                你是财富管理回答的润色编辑。
                你只负责优化表达清晰度和可读性。
                严禁修改任何数字、产品编码、产品名称、分配金额、百分比、币种或结论。
                严禁新增产品、推荐或事实。
                你必须严格基于给定的基础结果润色。
                只输出 JSON：
                {
                  "selectionSummary": "润色后的单句摘要",
                  "answer": "润色后的最终回答"
                }
                """;
    }

    private String buildSpecializedPolishUserPrompt(WealthInsight insight, SpecializedAdvisoryResult baseResult, String userMessage) {
        return """
                user_question:
                %s

                workflow_intent:
                %s

                candidate_products:
                %s

                final_recommendations:
                %s

                highlights:
                %s

                base_summary:
                %s

                base_answer:
                %s
                """.formatted(
                userMessage,
                insight.workflow().intentCode(),
                buildCandidateBlock(baseResult.candidateProducts()),
                baseResult.finalRecommendations(),
                baseResult.advisoryHighlights(),
                baseResult.selectionSummary(),
                baseResult.answer()
        );
    }

    private String buildUserPrompt(WealthInsight insight, String userMessage) {
        if (insight.language() == SupportedLanguage.EN) {
            return """
                    User question:
                    %s

                    Workflow intent:
                    %s

                    User risk level:
                    %s

                    Monthly cashflow analysis:
                    %s

                    Savings goal projection:
                    %s

                    Scenario analysis:
                    %s

                    Whether product recommendation is allowed this turn:
                    %s

                    Business highlights:
                    %s

                    RAG snippets:
                    %s

                    Candidate products (must select only from here):
                    %s
                    """.formatted(
                    userMessage,
                    insight.workflow().intentCode(),
                    insight.riskLevel(),
                    insight.monthlyAnalyses(),
                    insight.goalProjection(),
                    insight.goalScenarioAnalysis(),
                    insight.recommendProducts(),
                    insight.advisoryHighlights(),
                    insight.ragContextSnippets(),
                    buildCandidateBlock(insight.productRecommendations())
            );
        }
        return """
                用户问题：
                %s

                工作流意图：
                %s

                用户风险等级：
                %s

                月度收支分析：
                %s

                储蓄目标预测：
                %s

                场景测算结果：
                %s

                本轮是否允许推荐产品：
                %s

                业务建议摘要：
                %s

                RAG 检索片段：
                %s

                候选理财产品（只能从这里选）：
                %s
                """.formatted(
                userMessage,
                insight.workflow().intentCode(),
                insight.riskLevel(),
                insight.monthlyAnalyses(),
                insight.goalProjection(),
                insight.goalScenarioAnalysis(),
                insight.recommendProducts(),
                insight.advisoryHighlights(),
                insight.ragContextSnippets(),
                buildCandidateBlock(insight.productRecommendations())
        );
    }

    private String buildFallbackSummary(SupportedLanguage language, boolean emptySelections) {
        if (language == SupportedLanguage.EN) {
            return emptySelections
                    ? "No candidate product is available for recommendation at the moment."
                    : "Based on risk level, goal horizon, and cashflow capacity, one best-fit candidate product is prioritized.";
        }
        return emptySelections
                ? "当前没有可供推荐的候选产品。"
                : "基于风险等级、目标期限和现金流能力，优先推荐 1 个最匹配候选产品。";
    }

    private String buildGenericFallback(WealthInsight insight, String userMessage) {
        if (insight.language() == SupportedLanguage.EN) {
            return """
                    This is still a wealth-management question, and the right answer depends mainly on your goal horizon, risk tolerance, cashflow stability, and current allocation.
                    Based on your current risk level of %s, the safer approach is to avoid forcing product changes unless the decision clearly fits your time horizon and cashflow capacity.
                    If you want a more precise answer, the next useful detail would be your exact objective for this decision and any relevant amounts or existing holdings.
                    """.formatted(insight.riskLevel()).trim();
        }
        return """
                这是一个财富管理范围内的问题，核心判断通常取决于你的目标期限、风险承受能力、现金流稳定性以及当前配置情况。
                结合你当前的风险等级 %s，更稳妥的做法是不要急于做产品层面的调整，而是先确认这次决策是否与你的目标期限和现金流承受能力匹配。
                如果你想得到更精确的建议，下一步最有价值的信息是你的具体目标，以及相关金额或现有持仓情况。
                """.formatted(insight.riskLevel()).trim();
    }

    private String buildCandidateBlock(List<ProductRecommendation> candidates) {
        return "[\n" + candidates.stream()
                .map(item -> """
                        {
                          "productCode": "%s",
                          "productName": "%s",
                          "annualReturnRate": "%s",
                          "minHoldingDays": %d,
                          "liquidityLevel": "%s",
                          "screeningReason": "%s",
                          "complianceNote": "%s"
                        }
                        """.formatted(
                        item.product().getProductCode(),
                        item.product().getProductName(),
                        item.product().getAnnualReturnRate(),
                        item.product().getMinHoldingDays(),
                        item.product().getLiquidityLevel().name(),
                        item.reason(),
                        item.product().getComplianceNote()
                ))
                .collect(Collectors.joining(",\n")) + "\n]";
    }

    private String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private String safeText(String text) {
        return text == null || text.isBlank() ? "" : text.trim();
    }

    private record ParsedLlmResponse(
            String selectionSummary,
            List<ParsedSelection> finalRecommendations,
            String answer
    ) {
    }

    private record ParsedPolishResponse(
            String selectionSummary,
            String answer
    ) {
    }

    private record ParsedSelection(
            String productCode,
            String reason
    ) {
    }
}
