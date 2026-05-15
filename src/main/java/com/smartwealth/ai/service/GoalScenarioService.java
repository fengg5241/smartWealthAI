package com.smartwealth.ai.service;

import com.smartwealth.ai.domain.UserProfile;
import com.smartwealth.ai.service.model.GoalProjection;
import com.smartwealth.ai.service.model.GoalScenarioAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class GoalScenarioService {

    private static final Pattern APARTMENT_PATTERN =
            Pattern.compile("(\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)\\s*(k|m|万)?\\s*(sgd|usd|rmb|cny|人民币|新币|新加坡元)?", Pattern.CASE_INSENSITIVE);

    private final Clock clock;

    public GoalScenarioService(Clock clock) {
        this.clock = clock;
    }

    public GoalScenarioAnalysis analyze(String userMessage, UserProfile userProfile, GoalProjection goalProjection) {
        if (userMessage == null) {
            return defaultGoalScenario(userProfile, goalProjection);
        }

        String normalized = userMessage.toLowerCase(Locale.ROOT);
        if (!normalized.contains("公寓") && !normalized.contains("apartment") && !normalized.contains("首付")) {
            return defaultGoalScenario(userProfile, goalProjection);
        }

        Matcher matcher = APARTMENT_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return defaultGoalScenario(userProfile, goalProjection);
        }

        BigDecimal amount = new BigDecimal(matcher.group(1).replace(",", ""));
        String unit = matcher.group(2);
        if (unit != null) {
            if ("k".equalsIgnoreCase(unit)) {
                amount = amount.multiply(BigDecimal.valueOf(1000));
            } else if ("m".equalsIgnoreCase(unit)) {
                amount = amount.multiply(BigDecimal.valueOf(1_000_000));
            } else if ("万".equals(unit)) {
                amount = amount.multiply(BigDecimal.valueOf(10_000));
            }
        }
        String currency = normalizeCurrency(matcher.group(3));
        BigDecimal requiredDownPayment = amount.multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentBalance = userProfile.getAvailableSavingsBalance().setScale(2, RoundingMode.HALF_UP);
        BigDecimal gap = requiredDownPayment.subtract(currentBalance).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal contribution = goalProjection.projectedMonthlyContribution().max(BigDecimal.ONE);
        int monthsToReach = gap.signum() == 0
                ? 0
                : gap.divide(contribution, 0, RoundingMode.CEILING).intValue();

        return new GoalScenarioAnalysis(
                "Apartment Down Payment",
                amount.setScale(2, RoundingMode.HALF_UP),
                requiredDownPayment,
                currentBalance,
                gap,
                gap.signum() == 0,
                monthsToReach,
                LocalDate.now(clock).plusMonths(monthsToReach),
                currency
        );
    }

    private GoalScenarioAnalysis defaultGoalScenario(UserProfile userProfile, GoalProjection goalProjection) {
        BigDecimal currentBalance = userProfile.getAvailableSavingsBalance().setScale(2, RoundingMode.HALF_UP);
        return new GoalScenarioAnalysis(
                goalProjection.goalName(),
                goalProjection.targetAmount(),
                goalProjection.targetAmount(),
                currentBalance,
                goalProjection.targetAmount().subtract(currentBalance).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                currentBalance.compareTo(goalProjection.targetAmount()) >= 0,
                goalProjection.monthsToGoal(),
                goalProjection.projectedCompletionDate(),
                "CNY"
        );
    }

    private String normalizeCurrency(String rawCurrency) {
        if (rawCurrency == null || rawCurrency.isBlank()) {
            return "SGD";
        }
        return switch (rawCurrency.toUpperCase(Locale.ROOT)) {
            case "RMB", "CNY", "人民币" -> "CNY";
            case "新币", "新加坡元", "SGD" -> "SGD";
            case "USD" -> "USD";
            default -> rawCurrency.toUpperCase(Locale.ROOT);
        };
    }
}
