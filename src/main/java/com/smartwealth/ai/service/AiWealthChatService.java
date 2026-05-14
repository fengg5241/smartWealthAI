package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.ChatResponse;
import com.smartwealth.ai.api.response.FinalRecommendationView;
import com.smartwealth.ai.service.model.WealthInsight;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AiWealthChatService {

    private final WealthInsightService wealthInsightService;
    private final TransactionAnalysisService transactionAnalysisService;
    private final LlmAdvisoryService llmAdvisoryService;

    public AiWealthChatService(
            WealthInsightService wealthInsightService,
            TransactionAnalysisService transactionAnalysisService,
            LlmAdvisoryService llmAdvisoryService
    ) {
        this.wealthInsightService = wealthInsightService;
        this.transactionAnalysisService = transactionAnalysisService;
        this.llmAdvisoryService = llmAdvisoryService;
    }

    public ChatResponse chat(Long userId, String message) {
        WealthInsight insight = wealthInsightService.buildInsight(userId, message);
        var llmResult = llmAdvisoryService.advise(insight, message);
        List<FinalRecommendationView> finalRecommendedProducts = llmResult.finalRecommendations().stream()
                .map(item -> new FinalRecommendationView(
                        item.productCode(),
                        insight.productNameByCode().getOrDefault(item.productCode(), item.productCode()),
                        item.reason()
                ))
                .toList();

        return new ChatResponse(
                userId,
                insight.riskLevel(),
                insight.monthlyAnalyses().stream().map(transactionAnalysisService::toSummary).toList(),
                wealthInsightService.toView(insight.goalProjection()),
                insight.productRecommendations().stream().map(wealthInsightService::toView).toList(),
                finalRecommendedProducts,
                llmResult.selectionSummary(),
                insight.advisoryHighlights(),
                insight.ragContextSnippets(),
                llmResult.answer()
        );
    }
}
