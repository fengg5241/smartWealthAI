package com.smartwealth.ai.service;

import com.smartwealth.ai.domain.SavingsGoal;
import com.smartwealth.ai.repository.SavingsGoalRepository;
import com.smartwealth.ai.service.exception.ResourceNotFoundException;
import com.smartwealth.ai.service.model.GoalProjection;
import com.smartwealth.ai.service.model.MonthlyAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoalProjectionService {

    private static final BigDecimal MIN_CONTRIBUTION_FLOOR = new BigDecimal("100.00");

    private final SavingsGoalRepository savingsGoalRepository;
    private final Clock clock;

    public GoalProjectionService(SavingsGoalRepository savingsGoalRepository, Clock clock) {
        this.savingsGoalRepository = savingsGoalRepository;
        this.clock = clock;
    }

    public GoalProjection project(Long userId, List<MonthlyAnalysis> analyses) {
        SavingsGoal goal = savingsGoalRepository.findTopByUserIdOrderByTargetDateAsc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings goal not found for user " + userId));

        BigDecimal averageSavings = analyses.stream()
                .map(MonthlyAnalysis::netCashflow)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, analyses.size())), 2, RoundingMode.HALF_UP);

        BigDecimal projectedContribution = averageSavings.max(MIN_CONTRIBUTION_FLOOR).setScale(2, RoundingMode.HALF_UP);
        int monthsToGoal = projectedContribution.signum() <= 0
                ? Integer.MAX_VALUE
                : goal.getTargetAmount()
                .divide(projectedContribution, 0, RoundingMode.CEILING)
                .intValue();

        LocalDate today = LocalDate.now(clock);
        LocalDate projectedCompletionDate = monthsToGoal == Integer.MAX_VALUE
                ? today.plusYears(50)
                : today.plusMonths(monthsToGoal);

        boolean onTrack = !projectedCompletionDate.isAfter(goal.getTargetDate());

        return new GoalProjection(
                goal.getGoalName(),
                goal.getTargetAmount().setScale(2, RoundingMode.HALF_UP),
                goal.getTargetDate(),
                averageSavings,
                projectedContribution,
                monthsToGoal == Integer.MAX_VALUE
                        ? (int) ChronoUnit.MONTHS.between(today, projectedCompletionDate)
                        : monthsToGoal,
                projectedCompletionDate,
                onTrack
        );
    }
}
