package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.FinancialProduct;
import com.smartwealth.ai.domain.RiskLevel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialProductRepository extends JpaRepository<FinancialProduct, Long> {

    List<FinancialProduct> findBySupportedRiskLevelOrderByAnnualReturnRateDesc(RiskLevel riskLevel);
}
