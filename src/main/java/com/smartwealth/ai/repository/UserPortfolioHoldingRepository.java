package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.UserPortfolioHolding;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPortfolioHoldingRepository extends JpaRepository<UserPortfolioHolding, Long> {

    @EntityGraph(attributePaths = {"product"})
    List<UserPortfolioHolding> findByUserIdOrderByAllocationPercentDesc(Long userId);
}
