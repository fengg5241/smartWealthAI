package com.smartwealth.ai.api;

import com.smartwealth.ai.api.request.ChatRequest;
import com.smartwealth.ai.api.response.ChatResponse;
import com.smartwealth.ai.api.response.UserProfileView;
import com.smartwealth.ai.api.response.WealthOverviewResponse;
import com.smartwealth.ai.service.AiWealthChatService;
import com.smartwealth.ai.service.ChatSessionService;
import com.smartwealth.ai.service.TransactionAnalysisService;
import com.smartwealth.ai.service.UserProfileQueryService;
import com.smartwealth.ai.service.WealthInsightService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wealth-advisor")
public class WealthAdvisorController {

    private final AiWealthChatService aiWealthChatService;
    private final WealthInsightService wealthInsightService;
    private final TransactionAnalysisService transactionAnalysisService;
    private final UserProfileQueryService userProfileQueryService;
    private final ChatSessionService chatSessionService;

    public WealthAdvisorController(
            AiWealthChatService aiWealthChatService,
            WealthInsightService wealthInsightService,
            TransactionAnalysisService transactionAnalysisService,
            UserProfileQueryService userProfileQueryService,
            ChatSessionService chatSessionService
    ) {
        this.aiWealthChatService = aiWealthChatService;
        this.wealthInsightService = wealthInsightService;
        this.transactionAnalysisService = transactionAnalysisService;
        this.userProfileQueryService = userProfileQueryService;
        this.chatSessionService = chatSessionService;
    }

    @GetMapping("/users")
    public List<UserProfileView> users() {
        return userProfileQueryService.listUsers();
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return aiWealthChatService.chat(request.userId(), request.message(), request.sessionId());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void resetSession(@PathVariable String sessionId) {
        chatSessionService.clear(sessionId);
    }

    @GetMapping("/users/{userId}/overview")
    public WealthOverviewResponse overview(@PathVariable Long userId) {
        var insight = wealthInsightService.buildInsight(userId, "生成用户财富概览", List.of());
        return new WealthOverviewResponse(
                userId,
                insight.riskLevel(),
                insight.monthlyAnalyses().stream().map(transactionAnalysisService::toSummary).toList(),
                wealthInsightService.toView(insight.goalProjection()),
                wealthInsightService.toView(insight.goalScenarioAnalysis()),
                insight.productRecommendations().stream().map(wealthInsightService::toView).toList(),
                insight.advisoryHighlights()
        );
    }
}
