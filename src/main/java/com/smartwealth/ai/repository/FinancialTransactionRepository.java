package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.FinancialTransaction;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    List<FinancialTransaction> findByUserIdAndTransactionDateBetweenOrderByTransactionDateAsc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );
}
