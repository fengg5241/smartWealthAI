package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.ChatResponse;
import com.smartwealth.ai.api.response.ChatIntentType;
import com.smartwealth.ai.api.response.ChatMessageView;
import com.smartwealth.ai.api.response.FinalRecommendationView;
import com.smartwealth.ai.api.response.InvestmentPlanView;
import com.smartwealth.ai.service.model.ConversationMessage;
import com.smartwealth.ai.service.model.WealthInsight;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiWealthChatService {

    private final IntentRoutingService intentRoutingService;
    private final WealthInsightService wealthInsightService;
    private final TransactionAnalysisService transactionAnalysisService;
    private final LlmAdvisoryService llmAdvisoryService;
    private final ChatSessionService chatSessionService;
    private final ProductLinkFormatter productLinkFormatter;

    public AiWealthChatService(
            IntentRoutingService intentRoutingService,
            WealthInsightService wealthInsightService,
            TransactionAnalysisService transactionAnalysisService,
            LlmAdvisoryService llmAdvisoryService,
            ChatSessionService chatSessionService,
            ProductLinkFormatter productLinkFormatter
    ) {
        this.intentRoutingService = intentRoutingService;
        this.wealthInsightService = wealthInsightService;
        this.transactionAnalysisService = transactionAnalysisService;
        this.llmAdvisoryService = llmAdvisoryService;
        this.chatSessionService = chatSessionService;
        this.productLinkFormatter = productLinkFormatter;
    }

    public ChatResponse chat(Long userId, String message, String sessionId) {
        var session = chatSessionService.openOrCreate(userId, sessionId);
        List<String> historyMessages = session.messages().stream()
                .map(ConversationMessage::content)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        chatSessionService.appendUserMessage(session.sessionId(), message);
        var intent = intentRoutingService.detect(message, historyMessages);
        if (intent.type() == ChatIntentType.GENERAL_QA) {
            String reply = "当前产品仅支持财富管理相关问答，请围绕收支分析、储蓄目标、风险等级、理财建议或产品推荐进行提问。";
            chatSessionService.appendAssistantMessage(session.sessionId(), reply);
            return new ChatResponse(
                    userId,
                    session.sessionId(),
                    intent.type(),
                    intent.reason(),
                    null,
                    List.of(),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    "",
                    List.of(),
                    List.of(),
                    reply,
                    toMessageViews(chatSessionService.snapshot(session.sessionId()).messages())
            );
        }

        WealthInsight insight = wealthInsightService.buildInsight(userId, message, historyMessages);
        var llmResult = llmAdvisoryService.advise(insight, message);
        chatSessionService.appendAssistantMessage(session.sessionId(), llmResult.answer());
        List<InvestmentPlanView> investmentPlans = insight.investmentPlans().stream()
                .map(wealthInsightService::toView)
                .toList();
        List<FinalRecommendationView> finalRecommendedProducts = llmResult.finalRecommendations().stream()
                .map(item -> new FinalRecommendationView(
                        item.product().product().getProductCode(),
                        item.product().product().getProductName(),
                        productLinkFormatter.toDisplayName(item.product().product().getProductName()),
                        productLinkFormatter.purchaseLinkEnabled(),
                        item.product().product().getAnnualReturnRate(),
                        item.product().product().getMinHoldingDays(),
                        item.product().product().getLiquidityLevel().name(),
                        item.product().product().getComplianceNote(),
                        item.reason(),
                        investmentPlans.stream()
                                .filter(plan -> plan.productCode().equals(item.product().product().getProductCode()))
                                .findFirst()
                                .orElse(null)
                ))
                .toList();

        return new ChatResponse(
                userId,
                session.sessionId(),
                intent.type(),
                intent.reason(),
                insight.riskLevel(),
                insight.monthlyAnalyses().stream().map(transactionAnalysisService::toSummary).toList(),
                wealthInsightService.toView(insight.goalProjection()),
                wealthInsightService.toView(insight.goalScenarioAnalysis()),
                insight.productRecommendations().stream().map(wealthInsightService::toView).toList(),
                finalRecommendedProducts,
                investmentPlans,
                llmResult.selectionSummary(),
                insight.advisoryHighlights(),
                insight.ragContextSnippets(),
                llmResult.answer(),
                toMessageViews(chatSessionService.snapshot(session.sessionId()).messages())
        );
    }

    private List<ChatMessageView> toMessageViews(List<ConversationMessage> messages) {
        return messages.stream()
                .map(message -> new ChatMessageView(message.role(), message.content()))
                .toList();
    }
}
