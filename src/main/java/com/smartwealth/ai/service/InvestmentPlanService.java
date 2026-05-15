package com.smartwealth.ai.service;

import com.smartwealth.ai.service.model.GoalProjection;
import com.smartwealth.ai.service.model.GoalScenarioAnalysis;
import com.smartwealth.ai.service.model.InvestmentPlan;
import com.smartwealth.ai.service.model.ProductRecommendation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InvestmentPlanService {

    private final Clock clock;

    public InvestmentPlanService(Clock clock) {
        this.clock = clock;
    }

    public List<InvestmentPlan> buildPlans(
            ProductRecommendation recommendation,
            GoalScenarioAnalysis scenario,
            GoalProjection projection
    ) {
        BigDecimal maxMonthlyBudget = projection.projectedMonthlyContribution().setScale(2, RoundingMode.HALF_UP);
        List<BigDecimal> planAmounts = buildSuggestedMonthlyAmounts(maxMonthlyBudget);
        List<InvestmentPlan> plans = new ArrayList<>();
        for (BigDecimal monthlyInvestment : planAmounts) {
            plans.add(buildSinglePlan(recommendation, scenario, monthlyInvestment));
        }
        return plans;
    }

    private InvestmentPlan buildSinglePlan(
            ProductRecommendation recommendation,
            GoalScenarioAnalysis scenario,
            BigDecimal monthlyInvestment
    ) {
        BigDecimal annualRate = recommendation.product().getAnnualReturnRate();
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        BigDecimal currentValue = scenario.currentSavingsBalance();
        BigDecimal target = scenario.requiredDownPayment();
        BigDecimal gap = target.subtract(currentValue).max(BigDecimal.ZERO);
        int savingsOnlyMonths = gap.signum() == 0
                ? 0
                : gap.divide(monthlyInvestment, 0, RoundingMode.CEILING).intValue();
        int months = 0;
        BigDecimal investmentGain = BigDecimal.ZERO;

        while (currentValue.compareTo(target) < 0 && months < 240) {
            currentValue = currentValue.add(monthlyInvestment);
            BigDecimal gainThisMonth = currentValue.multiply(monthlyRate).setScale(10, RoundingMode.HALF_UP);
            currentValue = currentValue.add(gainThisMonth);
            investmentGain = investmentGain.add(gainThisMonth);
            months++;
        }

        int savedMonths = Math.max(0, savingsOnlyMonths - months);

        return new InvestmentPlan(
                recommendation.product().getProductCode(),
                recommendation.product().getProductName(),
                recommendation.product().getAnnualReturnRate(),
                monthlyInvestment,
                savingsOnlyMonths,
                months,
                investmentGain.setScale(2, RoundingMode.HALF_UP),
                currentValue.setScale(2, RoundingMode.HALF_UP),
                LocalDate.now(clock).plusMonths(months),
                savedMonths
        );
    }

    private List<BigDecimal> buildSuggestedMonthlyAmounts(BigDecimal maxMonthlyBudget) {
        BigDecimal upper = roundHundreds(maxMonthlyBudget.min(new BigDecimal("7000")));
        BigDecimal lower = roundHundreds(maxMonthlyBudget.min(new BigDecimal("5000")));

        List<BigDecimal> amounts = new ArrayList<>();
        if (upper.compareTo(BigDecimal.ZERO) > 0) {
            amounts.add(upper);
        }
        if (lower.compareTo(BigDecimal.ZERO) > 0 && lower.compareTo(upper) != 0) {
            amounts.add(lower);
        }
        if (amounts.isEmpty()) {
            amounts.add(roundHundreds(maxMonthlyBudget.max(new BigDecimal("1000"))));
        }
        return amounts;
    }

    private BigDecimal roundHundreds(BigDecimal value) {
        return value.divide(new BigDecimal("100"), 0, RoundingMode.DOWN)
                .multiply(new BigDecimal("100"))
                .max(new BigDecimal("1000"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
