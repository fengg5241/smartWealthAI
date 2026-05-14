package com.smartwealth.ai.service;

import com.smartwealth.ai.config.WealthAdvisorProperties;
import com.smartwealth.ai.domain.FinancialProduct;
import com.smartwealth.ai.domain.RiskLevel;
import com.smartwealth.ai.domain.SavingsGoal;
import com.smartwealth.ai.domain.UserProfile;
import com.smartwealth.ai.repository.FinancialProductRepository;
import com.smartwealth.ai.repository.SavingsGoalRepository;
import com.smartwealth.ai.repository.UserProfileRepository;
import com.smartwealth.ai.service.exception.ResourceNotFoundException;
import com.smartwealth.ai.service.model.RagSnippet;
import java.time.LocalDate;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagKnowledgeService {

    private final VectorStore vectorStore;
    private final UserProfileRepository userProfileRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final FinancialProductRepository financialProductRepository;
    private final WealthAdvisorProperties properties;

    public RagKnowledgeService(
            VectorStore vectorStore,
            UserProfileRepository userProfileRepository,
            SavingsGoalRepository savingsGoalRepository,
            FinancialProductRepository financialProductRepository,
            WealthAdvisorProperties properties
    ) {
        this.vectorStore = vectorStore;
        this.userProfileRepository = userProfileRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.financialProductRepository = financialProductRepository;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapKnowledgeBase() {
        if (!properties.getRag().isBootstrapEnabled()) {
            return;
        }

        List<Document> documents = new ArrayList<>();
        for (UserProfile user : userProfileRepository.findAll()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "risk-profile");
            metadata.put("userId", user.getId());
            metadata.put("riskLevel", user.getRiskLevel().name());
            metadata.put("fullName", user.getFullName());
            documents.add(new Document(
                    stableUuid("USER-RISK-" + user.getId()),
                    """
                    User risk profile:
                    userId: %d
                    fullName: %s
                    riskLevel: %s
                    advisoryRule: Only recommend products whose supported risk level is exactly aligned with the user risk level.
                    """.formatted(user.getId(), user.getFullName(), user.getRiskLevel()),
                    metadata
            ));
        }

        for (SavingsGoal goal : savingsGoalRepository.findAll()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "savings-goal");
            metadata.put("userId", goal.getUser().getId());
            metadata.put("riskLevel", goal.getUser().getRiskLevel().name());
            metadata.put("goalName", goal.getGoalName());
            documents.add(new Document(
                    stableUuid("GOAL-" + goal.getId()),
                    """
                    User savings goal:
                    userId: %d
                    goalName: %s
                    targetAmount: %s
                    targetDate: %s
                    """.formatted(
                            goal.getUser().getId(),
                            goal.getGoalName(),
                            goal.getTargetAmount().setScale(2, RoundingMode.HALF_UP),
                            goal.getTargetDate()
                    ),
                    metadata
            ));
        }

        for (FinancialProduct product : financialProductRepository.findAll()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "financial-product");
            metadata.put("productId", product.getId());
            metadata.put("riskLevel", product.getSupportedRiskLevel().name());
            metadata.put("productCode", product.getProductCode());
            documents.add(new Document(
                    stableUuid("PRODUCT-" + product.getId()),
                    """
                    Product profile:
                    productCode: %s
                    productName: %s
                    supportedRiskLevel: %s
                    annualReturnRate: %s
                    minHoldingDays: %d
                    liquidityLevel: %s
                    complianceNote: %s
                    description: %s
                    """.formatted(
                            product.getProductCode(),
                            product.getProductName(),
                            product.getSupportedRiskLevel(),
                            product.getAnnualReturnRate().setScale(4, RoundingMode.HALF_UP),
                            product.getMinHoldingDays(),
                            product.getLiquidityLevel(),
                            product.getComplianceNote(),
                            product.getDescription()
                    ),
                    metadata
            ));
        }

        if (!documents.isEmpty()) {
            vectorStore.delete(documents.stream().map(Document::getId).toList());
            vectorStore.add(documents);
        }
    }

    public RiskLevel getRiskLevel(Long userId) {
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("risk profile for user %d".formatted(userId))
                .topK(1)
                .build());
        for (Document document : results) {
            Object matchUserId = document.getMetadata().get("userId");
            if (matchUserId != null && Long.parseLong(matchUserId.toString()) == userId) {
                Object riskLevel = document.getMetadata().get("riskLevel");
                if (riskLevel != null) {
                    return RiskLevel.valueOf(riskLevel.toString());
                }
            }
        }

        return userProfileRepository.findById(userId)
                .map(UserProfile::getRiskLevel)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    public List<RagSnippet> retrieveUserContext(Long userId, String question) {
        RiskLevel riskLevel = getRiskLevel(userId);
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question + " riskLevel=" + riskLevel.name() + " userId=" + userId)
                .topK(properties.getRag().getTopK())
                .similarityThreshold(properties.getRag().getSimilarityThreshold())
                .build());

        return documents.stream()
                .filter(document -> isRelevant(document, userId, riskLevel))
                .map(document -> new RagSnippet(document.getId(), document.getText(), document.getMetadata()))
                .toList();
    }

    private boolean isRelevant(Document document, Long userId, RiskLevel riskLevel) {
        Object documentUserId = document.getMetadata().get("userId");
        if (documentUserId != null && Long.parseLong(documentUserId.toString()) == userId) {
            return true;
        }

        Object documentRiskLevel = document.getMetadata().get("riskLevel");
        return documentRiskLevel != null && riskLevel.name().equals(documentRiskLevel.toString());
    }

    private String stableUuid(String rawId) {
        return UUID.nameUUIDFromBytes(rawId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
