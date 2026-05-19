package com.smartwealth.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartwealth.ai.domain.RiskLevel;
import com.smartwealth.ai.domain.UserProfile;
import com.smartwealth.ai.service.model.GoalProjection;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GoalScenarioServiceTest {

    @Test
    void shouldCalculateEightHundredKCondoDownPaymentCorrectly() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), ZoneOffset.UTC);
        GoalScenarioService goalScenarioService = new GoalScenarioService(fixedClock);

        UserProfile userProfile = new UserProfile();
        userProfile.setRiskLevel(RiskLevel.CONSERVATIVE);
        userProfile.setAvailableSavingsBalance(new BigDecimal("3000.00"));

        GoalProjection projection = new GoalProjection(
                "Emergency Reserve Upgrade",
                new BigDecimal("90000.00"),
                LocalDate.of(2027, 5, 19),
                new BigDecimal("7935.00"),
                new BigDecimal("7935.00"),
                12,
                LocalDate.of(2027, 5, 19),
                true
        );

        var scenario = goalScenarioService.analyze("can I afford a $800k condo", userProfile, projection);

        assertThat(scenario.assetPrice()).isEqualByComparingTo("800000.00");
        assertThat(scenario.requiredDownPayment()).isEqualByComparingTo("200000.00");
        assertThat(scenario.currentSavingsBalance()).isEqualByComparingTo("3000.00");
        assertThat(scenario.savingsGap()).isEqualByComparingTo("197000.00");
        assertThat(scenario.affordableNow()).isFalse();
        assertThat(scenario.estimatedMonthsToReachGoal()).isEqualTo(25);
        assertThat(scenario.estimatedReachDate()).isEqualTo(LocalDate.of(2028, 6, 19));
    }
}
