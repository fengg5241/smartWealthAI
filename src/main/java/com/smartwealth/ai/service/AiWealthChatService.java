package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.ChatResponse;
import com.smartwealth.ai.api.response.ChatIntentType;
import com.smartwealth.ai.api.response.ChatMessageView;
import com.smartwealth.ai.api.response.FinalRecommendationView;
import com.smartwealth.ai.api.response.InvestmentPlanView;
import com.smartwealth.ai.config.WealthAdvisorProperties;
import com.smartwealth.ai.service.model.ChatRequestContext;
import com.smartwealth.ai.service.model.ConversationMessage;
import com.smartwealth.ai.service.model.IntentClassificationResult;
import com.smartwealth.ai.service.model.ResponsePolicy;
import com.smartwealth.ai.service.model.SupportedLanguage;
import com.smartwealth.ai.service.model.WealthInsight;
import com.smartwealth.ai.service.model.WealthIntentCode;
import com.smartwealth.ai.service.model.WealthWorkflow;
import com.smartwealth.ai.service.model.SpecializedAdvisoryResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private final AdvisoryNarrativeService advisoryNarrativeService;
    private final WealthAdvisorProperties wealthAdvisorProperties;
    private final Clock clock;
    private final ChatSessionService chatSessionService;
    private final ProductLinkFormatter productLinkFormatter;

    public AiWealthChatService(
            IntentRoutingService intentRoutingService,
            IntentClassificationService intentClassificationService,
            WealthInsightService wealthInsightService,
            TransactionAnalysisService transactionAnalysisService,
            LlmAdvisoryService llmAdvisoryService,
            SpecializedAdvisoryService specializedAdvisoryService,
            AdvisoryNarrativeService advisoryNarrativeService,
            WealthAdvisorProperties wealthAdvisorProperties,
            Clock clock,
            ChatSessionService chatSessionService,
            ProductLinkFormatter productLinkFormatter
    ) {
        this.intentRoutingService = intentRoutingService;
        this.intentClassificationService = intentClassificationService;
        this.wealthInsightService = wealthInsightService;
        this.transactionAnalysisService = transactionAnalysisService;
        this.llmAdvisoryService = llmAdvisoryService;
        this.specializedAdvisoryService = specializedAdvisoryService;
        this.advisoryNarrativeService = advisoryNarrativeService;
        this.wealthAdvisorProperties = wealthAdvisorProperties;
        this.clock = clock;
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
        WealthWorkflow workflow = toWorkflow(requestContext.classification());
        WealthInsight insight = wealthInsightService.buildInsight(
                userId,
                message,
                historyMessages,
                requestContext.language(),
                workflow
        );
        if (wealthAdvisorProperties.getChat().isOpenMode()) {
            if (workflow.responsePolicy() == ResponsePolicy.SAFE_DECLINE) {
                String answer = workflow.intentCode() == WealthIntentCode.OUT_OF_SCOPE
                        ? buildOutOfScopeReply(requestContext.language())
                        : buildSafeDeclineReply(requestContext.language(), workflow.intentCode());
                chatSessionService.appendAssistantMessage(session.sessionId(), answer);
                return buildSimpleResponse(userId, session.sessionId(), workflow, insight, answer);
            }
            String answer = llmAdvisoryService.generateOpenModeAnswer(
                    insight,
                    message,
                    session.messages(),
                    LocalDate.now(clock)
            );
            chatSessionService.appendAssistantMessage(session.sessionId(), answer);
            return buildSimpleResponse(userId, session.sessionId(), workflow, insight, answer);
        }
        if (!wealthAdvisorProperties.getChat().isOpenMode() && workflow.responsePolicy() == ResponsePolicy.SAFE_DECLINE) {
            String answer = workflow.intentCode() == WealthIntentCode.OUT_OF_SCOPE
                    ? buildOutOfScopeReply(requestContext.language())
                    : buildSafeDeclineReply(requestContext.language(), workflow.intentCode());
            chatSessionService.appendAssistantMessage(session.sessionId(), answer);
            return buildSimpleResponse(userId, session.sessionId(), workflow, insight, answer);
        }

        SpecializedAdvisoryResult specialized = workflow.useSpecializedHandler()
                ? specializedAdvisoryService.advise(insight, message).orElse(null)
                : null;
        if (specialized != null) {
            specialized = llmAdvisoryService.polishSpecializedAnswer(insight, specialized, message);
        }
        var llmResult = specialized == null && workflow.responsePolicy() != ResponsePolicy.GENERIC_WEALTH_GUIDANCE
                ? llmAdvisoryService.advise(insight, message)
                : null;
        String genericGuidance = specialized == null && workflow.responsePolicy() == ResponsePolicy.GENERIC_WEALTH_GUIDANCE
                ? llmAdvisoryService.generateGenericWealthGuidance(insight, message)
                : null;
        String answer = specialized != null ? specialized.answer()
                : genericGuidance != null ? genericGuidance
                : llmResult.answer();
        chatSessionService.appendAssistantMessage(session.sessionId(), answer);
        var finalSelections = specialized != null
                ? specialized.finalRecommendations()
                : llmResult != null ? llmResult.finalRecommendations() : List.<com.smartwealth.ai.service.model.LlmProductSelection>of();
        var candidateProducts = specialized != null
                ? specialized.candidateProducts()
                : genericGuidance != null ? List.<com.smartwealth.ai.service.model.ProductRecommendation>of() : insight.productRecommendations();
        String selectionSummary = specialized != null
                ? specialized.selectionSummary()
                : genericGuidance != null ? "" : llmResult.selectionSummary();
        List<String> highlights = specialized != null
                ? specialized.advisoryHighlights()
                : insight.advisoryHighlights();
        List<InvestmentPlanView> investmentPlans = (specialized == null ? insight.investmentPlans() : specialized.investmentPlans()).stream()
                .map(wealthInsightService::toView)
                .toList();
        List<String> investmentPlanSummaries = specialized != null
                ? specialized.investmentPlanSummaries()
                : buildGenericInvestmentPlanSummaries(insight, investmentPlans);
        List<FinalRecommendationView> finalRecommendedProducts = finalSelections.stream()
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
                candidateProducts.stream().map(wealthInsightService::toView).toList(),
                finalRecommendedProducts,
                investmentPlans,
                investmentPlanSummaries,
                selectionSummary,
                highlights,
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
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), classification.responsePolicy(), true, false, false, false);
            case CASHFLOW_ANALYSIS -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), classification.responsePolicy(), true, false, false, false);
            case GOAL_PROGRESS, GOAL_FEASIBILITY -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), classification.responsePolicy(), true, false, false, false);
            case PRODUCT_RECOMMENDATION -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), classification.responsePolicy(), true, true, false, false);
            case RISK_REBALANCING -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), classification.responsePolicy(), true, true, true, false);
            case FUND_SELECTION -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), classification.responsePolicy(), true, false, false, true);
            case PRODUCT_COMPARISON -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), classification.responsePolicy(), true, false, false, true);
            case PORTFOLIO_REBALANCING -> new WealthWorkflow(
                    ChatIntentType.WEALTH_ADVISORY, code, classification.intentName(), classification.reason(), classification.responsePolicy(), true, false, false, true);
            case OUT_OF_SCOPE -> new WealthWorkflow(
                    ChatIntentType.GENERAL_QA, code, classification.intentName(), classification.reason(), classification.responsePolicy(), false, false, false, false);
        };
    }

    private ChatResponse buildSimpleResponse(Long userId, String sessionId, WealthWorkflow workflow, WealthInsight insight, String answer) {
        return new ChatResponse(
                userId,
                sessionId,
                workflow.responseIntentType(),
                workflow.intentReason(),
                insight.riskLevel(),
                insight.monthlyAnalyses().stream().map(transactionAnalysisService::toSummary).toList(),
                wealthInsightService.toView(insight.goalProjection()),
                wealthInsightService.toView(insight.goalScenarioAnalysis()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                insight.advisoryHighlights(),
                insight.ragContextSnippets(),
                answer,
                toMessageViews(chatSessionService.snapshot(sessionId).messages())
        );
    }

    private String generateOpenGeneralAnswer(String message, List<String> historyMessages, SupportedLanguage language) {
        if (looksLikeDateQuestion(message)) {
            LocalDate today = LocalDate.now(clock);
            return language == SupportedLanguage.EN
                    ? "Today is %s.".formatted(today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")))
                    : "今天是 %s。".formatted(today.format(DateTimeFormatter.ofPattern("yyyy年M月d日")));
        }
        return llmAdvisoryService.generateGeneralOpenAnswer(message, historyMessages, language, LocalDate.now(clock));
    }

    private boolean looksLikeDateQuestion(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("what's the day today")
                || normalized.contains("what is the day today")
                || normalized.contains("what's today")
                || normalized.contains("what is today")
                || normalized.contains("today's date")
                || normalized.contains("what day is it")
                || normalized.contains("date today");
    }

    private List<String> buildGenericInvestmentPlanSummaries(WealthInsight insight, List<InvestmentPlanView> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        String currency = insight.goalScenarioAnalysis().currency();
        return advisoryNarrativeService.buildInvestmentPlanSummaries(
                insight.language(),
                insight.investmentPlans(),
                currency
        );
    }

    private String buildClarifyReply(SupportedLanguage language, WealthIntentCode intentCode) {
        if (language == SupportedLanguage.EN) {
            return switch (intentCode) {
                case WEALTH_OVERVIEW -> "This is a valid wealth-management question, but I need one more key detail to answer it well. Please tell me the exact amounts or existing holdings/debt related to this decision.";
                default -> "I can help with this wealth-management question, but I need one more key detail first. Please share the most relevant amount, time horizon, or existing holdings/debt for this decision.";
            };
        }
        return switch (intentCode) {
            case WEALTH_OVERVIEW -> "这是一个有效的理财问题，但要回答得更准确，我还需要一个关键补充信息。请告诉我这次决策相关的具体金额，或你的现有持仓/负债情况。";
            default -> "这个理财问题我可以继续回答，但还需要一个关键补充信息。请补充最相关的金额、期限，或现有持仓/负债情况。";
        };
    }

    private String buildSafeDeclineReply(SupportedLanguage language, WealthIntentCode intentCode) {
        if (language == SupportedLanguage.EN) {
            return switch (intentCode) {
                case WEALTH_OVERVIEW -> "This is still a wealth-management question, but the current product does not have the data or rule support needed to answer it safely. I can still help with general principles, cashflow analysis, savings goals, risk level questions, or product comparisons already supported here.";
                default -> "This is related to wealth management, but the current product should not answer it directly because the required data or rule support is missing. I can still help with supported topics such as cashflow analysis, savings goals, risk level, and in-product recommendations.";
            };
        }
        return switch (intentCode) {
            case WEALTH_OVERVIEW -> "这仍然属于理财问题，但当前产品缺少安全回答它所需的数据或规则支持，因此不适合直接给出结论。我仍然可以继续帮助你处理当前已支持的收支分析、储蓄目标、风险等级和产品比较等问题。";
            default -> "这属于理财相关问题，但当前产品缺少直接回答它所需的数据或规则支持，因此不适合直接给出结论。我仍然可以继续帮助你处理当前已支持的收支分析、储蓄目标、风险等级和站内产品建议等问题。";
        };
    }

    private String buildOutOfScopeReply(SupportedLanguage language) {
        if (language == SupportedLanguage.EN) {
            return "I am a wealth-management AI advisor. I can answer questions about investing, savings goals, "
                    + "cashflow analysis, product recommendations, risk assessment, and portfolio management. "
                    + "If you have a question in one of these areas, please ask and I will be happy to help.";
        }
        return "我是财富管理领域的 AI 顾问，目前只回答与投资理财、储蓄目标、收支分析、产品推荐、风险评估和持仓管理相关的问题。"
                + "如果你有这些方面的问题，我很乐意帮你解答。";
    }

    private List<ChatMessageView> toMessageViews(List<ConversationMessage> messages) {
        return messages.stream()
                .map(message -> new ChatMessageView(message.role(), message.content()))
                .toList();
    }
}
