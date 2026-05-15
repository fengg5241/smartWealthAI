package com.smartwealth.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwealth.ai.service.model.LlmAdvisoryResult;
import com.smartwealth.ai.service.model.LlmProductSelection;
import com.smartwealth.ai.service.model.ProductRecommendation;
import com.smartwealth.ai.service.model.WealthInsight;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LlmAdvisoryService {

    private static final Logger log = LoggerFactory.getLogger(LlmAdvisoryService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AdvisoryNarrativeService advisoryNarrativeService;

    public LlmAdvisoryService(
            ChatClient chatClient,
            ObjectMapper objectMapper,
            AdvisoryNarrativeService advisoryNarrativeService
    ) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.advisoryNarrativeService = advisoryNarrativeService;
    }

    public LlmAdvisoryResult advise(WealthInsight insight, String userMessage) {
        LlmAdvisoryResult fallback = buildFallback(insight, userMessage);
        log.info("Calling LLM for userId={}, candidates={}, ragSnippets={}",
                insight.userId(),
                insight.productRecommendations().size(),
                insight.ragContextSnippets().size());
        String systemPrompt = """
                你是财富管理领域的 AI 财富顾问。
                你必须基于给定的用户收支分析、储蓄目标、风险等级、RAG 检索上下文和候选理财产品做最终推荐。
                如果当前问题只是判断“现在能否支付/是否足够”，且没有主动要求产品方案或实现路径，则不要主动推荐产品。
                只有用户主动询问“如何实现目标”“推荐产品”“风险过高如何调整”等内容时，才给出产品推荐。
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

        String userPrompt = """
                用户问题：
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
                insight.riskLevel(),
                insight.monthlyAnalyses(),
                insight.goalProjection(),
                insight.goalScenarioAnalysis(),
                insight.recommendProducts(),
                insight.advisoryHighlights(),
                insight.ragContextSnippets(),
                buildCandidateBlock(insight.productRecommendations())
        );

        try {
            String raw = chatClient.prompt()
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

    private LlmAdvisoryResult buildFallback(WealthInsight insight, String userMessage) {
        List<LlmProductSelection> selections = insight.productRecommendations().stream()
                .limit(1)
                .map(item -> new LlmProductSelection(item, item.reason()))
                .toList();

        String answer = advisoryNarrativeService.buildFallbackAnswer(
                insight.userId(),
                insight.monthlyAnalyses(),
                insight.goalProjection(),
                insight.goalScenarioAnalysis(),
                insight.productRecommendations(),
                insight.investmentPlans(),
                insight.advisoryHighlights(),
                insight.ragContextSnippets(),
                userMessage
        );

        String summary = selections.isEmpty()
                ? "当前没有可供推荐的候选产品。"
                : "基于风险等级、目标期限和现金流能力，优先推荐 1 个最匹配候选产品。";

        return new LlmAdvisoryResult(selections, summary, answer);
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

    private record ParsedSelection(
            String productCode,
            String reason
    ) {
    }
}
