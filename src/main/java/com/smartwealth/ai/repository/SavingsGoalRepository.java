package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.SavingsGoal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    Optional<SavingsGoal> findTopByUserIdOrderByTargetDateAsc(Long userId);
}
