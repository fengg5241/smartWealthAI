package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.ChatResponse;
import com.smartwealth.ai.api.response.ChatIntentType;
import com.smartwealth.ai.api.response.ChatMessageView;
import com.smartwealth.ai.api.response.FinalRecommendationView;
import com.smartwealth.ai.api.response.InvestmentPlanView;
import com.smartwealth.ai.service.model.ChatRequestContext;
import com.smartwealth.ai.service.model.ConversationMessage;
import com.smartwealth.ai.service.model.IntentClassificationResult;
import com.smartwealth.ai.service.model.SupportedLanguage;
import com.smartwealth.ai.service.model.WealthInsight;
import com.smartwealth.ai.service.model.WealthIntentCode;
import com.smartwealth.ai.service.model.WealthWorkflow;
import com.smartwealth.ai.service.model.SpecializedAdvisoryResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiWealthChatService {

    private final IntentRoutingService intentRoutingService;
    private final IntentClassificationService intentClassificationService;
    private final WealthInsightService wealthInsightService;
    private final TransactionAnalysisService transactionAnalysisService;
    private final LlmAdvisoryService llmAdvisoryService;
    private final SpecializedAdvisoryService specializedAdvisoryService;
    private final ChatSessionService chatSessionService;
    private final ProductLinkFormatter productLinkFormatter;

    public AiWealthChatService(
            IntentRoutingService intentRoutingService,
            IntentClassificationService intentClassificationService,
            WealthInsightService wealthInsightService,
            TransactionAnalysisService transactionAnalysisService,
            LlmAdvisoryService llmAdvisoryService,
            SpecializedAdvisoryService specializedAdvisoryService,
            ChatSessionService chatSessionService,
            ProductLinkFormatter productLinkFormatter
    ) {
        this.intentRoutingService = intentRoutingService;
        this.intentClassificationService = intentClassificationService;
        this.wealthInsightService = wealthInsightService;
        this.transactionAnalysisService = transactionAnalysisService;
        this.llmAdvisoryService = llmAdvisoryService;
        this.specializedAdvisoryService = specializedAdvisoryService;
        this.chatSessionService = chatSessionService;
        this.productLinkFormatter = productLinkFormatter;
    }

    public ChatResponse chat(Long userId, String message, String sessionId) {
        var session = chatSessionService.openOrCreate(userId, sessionId);
        List<String> historyMessages = session.messages().stream()
                .filter(item -> "user".equalsIgnoreCase(item.role()))
                .map(ConversationMessage::content)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        chatSessionService.appendUserMessage(session.sessionId(), message);
        ChatRequestContext requestContext = buildRequestContext(message, historyMessages);
        if (requestContext.classification().intentCode() == WealthIntentCode.OUT_OF_SCOPE) {
            String reply = buildOutOfScopeReply(requestContext.language());
            chatSessionService.appendAssistantMessage(session.sessionId(), reply);
            return new ChatResponse(
                    userId,
                    session.sessionId(),
                    ChatIntentType.GENERAL_QA,
                    requestContext.classification().reason(),
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

        WealthWorkflow workflow = toWorkflow(requestContext.classification());
        WealthInsight insight = wealthInsightService.buildInsight(
                userId,
                message,
                historyMessages,
                requestContext.language(),
                workflow
        );
        SpecializedAdvisoryResult specialized = workflow.useSpecializedHandler()
                ? specializedAdvisoryService.advise(insight, message).orElse(null)
                : null;
        if (specialized != null) {
            specialized = llmAdvisoryService.polishSpecializedAnswer(insight, specialized, message);
        }
        var llmResult = specialized == null ? llmAdvisoryService.advise(insight, message) : null;
        String answer = specialized == null ? llmResult.answer() : specialized.answer();
        chatSessionService.appendAssistantMessage(session.sessionId(), answer);
        List<InvestmentPlanView> investmentPlans = (specialized == null ? insight.investmentPlans() : specialized.investmentPlans()).stream()
                .map(wealthInsightService::toView)
                .toList();
        List<FinalRecommendationView> finalRecommendedProducts = (specialized == null ? llmResult.finalRecommendations() : specialized.finalRecommendations()).stream()
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
                workflow.responseIntentType(),
                workflow.intentReason(),
                insight.riskLevel(),
                insight.monthlyAnalyses().stream().map(transactionAnalysisService::toSummary).toList(),
                wealthInsightService.toView(insight.goalProjection()),
                wealthInsightService.toView(insight.goalScenarioAnalysis()),
                (specialized == null ? insight.productRecommendations() : specialized.candidateProducts()).stream().map(wealthInsightService::toView).toList(),
                finalRecommendedProducts,
                investmentPlans,
                specialized == null ? llmResult.selectionSummary() : specialized.selectionSummary(),
                specialized == null ? insight.advisoryHighlights() : specialized.advisoryHighlights(),
                insight.ragContextSnippets(),
                answer,
                toMessageViews(chatSessionService.snapshot(session.sessionId()).messages())
        );
    }

    private ChatRequestContext buildRequestContext(String message, List<String> historyMessages) {
        SupportedLanguage language = intentRoutingService.detectLanguage(message);
        IntentClassificationResult classification = intentClassificationService.classify(message, historyMessages, language);
        return new ChatRequestContext(language, classification);
    }

    private WealthWorkflow toWorkflow(IntentClassificationResult classification) {
        WealthIntentCode code = classification.intentCode();
        return switch (code) {
            case WEALTH_OVERVIEW -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), true, false, false, false);
            case CASHFLOW_ANALYSIS -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), true, false, false, false);
            case GOAL_PROGRESS, GOAL_FEASIBILITY -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), true, false, false, false);
            case PRODUCT_RECOMMENDATION -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), true, true, false, false);
            case RISK_REBALANCING -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), true, true, true, false);
            case FUND_SELECTION -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), true, false, false, true);
            case PRODUCT_COMPARISON -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), true, false, false, true);
            case PORTFOLIO_REBALANCING -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), true, false, false, true);
            case OUT_OF_SCOPE -> new WealthWorkflow(
                    ChatIntentType.GENERAL_QA, code, classification.intentName(), classification.reason(), false, false, false, false);
        };
    }

    private String buildOutOfScopeReply(SupportedLanguage language) {
        if (language == SupportedLanguage.EN) {
            return "This product currently supports wealth-management questions only. Please ask about cashflow analysis, savings goals, risk level, financial advice, or product recommendations.";
        }
        return "当前产品仅支持财富管理相关问答，请围绕收支分析、储蓄目标、风险等级、理财建议或产品推荐进行提问。";
    }

    private List<ChatMessageView> toMessageViews(List<ConversationMessage> messages) {
        return messages.stream()
                .map(message -> new ChatMessageView(message.role(), message.content()))
                .toList();
    }
}
