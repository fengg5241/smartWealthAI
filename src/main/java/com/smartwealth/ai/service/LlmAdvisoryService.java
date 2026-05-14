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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LlmAdvisoryService {

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
        String systemPrompt = """
                你是财富管理领域的 AI 财富顾问。
                你必须基于给定的用户收支分析、储蓄目标、风险等级、RAG 检索上下文和候选理财产品做最终推荐。
                你只能从候选理财产品中选择最终推荐产品，严禁输出候选列表之外的产品编码。
                你的回答必须体现合规性，明确说明风险提示，并结合储蓄目标解释推荐原因。
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
                return fallback;
            }

            ParsedLlmResponse parsed = objectMapper.readValue(stripCodeFence(raw), ParsedLlmResponse.class);
            Map<String, ProductRecommendation> candidates = insight.productRecommendations().stream()
                    .collect(Collectors.toMap(item -> item.product().getProductCode(), Function.identity()));

            List<LlmProductSelection> validatedSelections = parsed.finalRecommendations() == null
                    ? List.of()
                    : parsed.finalRecommendations().stream()
                    .filter(item -> item.productCode() != null && candidates.containsKey(item.productCode()))
                    .map(item -> new LlmProductSelection(item.productCode(), safeText(item.reason())))
                    .toList();

            if (validatedSelections.isEmpty()) {
                return fallback;
            }

            return new LlmAdvisoryResult(
                    validatedSelections,
                    safeText(parsed.selectionSummary()),
                    safeText(parsed.answer())
            );
        } catch (Exception exception) {
            return fallback;
        }
    }

    private LlmAdvisoryResult buildFallback(WealthInsight insight, String userMessage) {
        List<LlmProductSelection> selections = insight.productRecommendations().stream()
                .limit(1)
                .map(item -> new LlmProductSelection(item.product().getProductCode(), item.reason()))
                .toList();

        String answer = advisoryNarrativeService.buildFallbackAnswer(
                insight.userId(),
                insight.monthlyAnalyses(),
                insight.goalProjection(),
                insight.productRecommendations(),
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
