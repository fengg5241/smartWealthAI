package com.smartwealth.ai.service;

import com.smartwealth.ai.config.WealthAdvisorProperties;
import com.smartwealth.ai.domain.FinancialProduct;
import com.smartwealth.ai.domain.RiskLevel;
import com.smartwealth.ai.repository.FinancialProductRepository;
import com.smartwealth.ai.service.model.GoalProjection;
import com.smartwealth.ai.service.model.ProductRecommendation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductRecommendationService {

    private final FinancialProductRepository financialProductRepository;
    private final WealthAdvisorProperties properties;
    private final Clock clock;

    public ProductRecommendationService(
            FinancialProductRepository financialProductRepository,
            WealthAdvisorProperties properties,
            Clock clock
    ) {
        this.financialProductRepository = financialProductRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public List<ProductRecommendation> recommend(RiskLevel riskLevel, GoalProjection goalProjection) {
        long targetDays = ChronoUnit.DAYS.between(LocalDate.now(clock), goalProjection.targetDate());

        return financialProductRepository.findBySupportedRiskLevelOrderByAnnualReturnRateDesc(riskLevel).stream()
                .sorted(Comparator
                        .comparing((FinancialProduct product) -> holdingFitScore(product, targetDays))
                        .thenComparing(FinancialProduct::getAnnualReturnRate)
                        .reversed())
                .limit(properties.getRecommendation().getMaxProducts())
                .map(product -> new ProductRecommendation(product, buildReason(product, goalProjection, targetDays)))
                .toList();
    }

    private long holdingFitScore(FinancialProduct product, long targetDays) {
        long difference = Math.abs(product.getMinHoldingDays() - Math.max(30L, targetDays / 3));
        return -difference;
    }

    private String buildReason(FinancialProduct product, GoalProjection projection, long targetDays) {
        BigDecimal monthlyContribution = projection.projectedMonthlyContribution();
        return """
                Product matches %s risk profile, offers annualized return %s%%, and suits a target horizon of about %d days.
                Suggested because your projected monthly contribution is %s and the product minimum holding period is %d days.
                """.formatted(
                product.getSupportedRiskLevel().name(),
                product.getAnnualReturnRate().movePointRight(2).stripTrailingZeros().toPlainString(),
                Math.max(targetDays, 0),
                monthlyContribution.toPlainString(),
                product.getMinHoldingDays()
        ).replace("\n", " ").trim();
    }
}
