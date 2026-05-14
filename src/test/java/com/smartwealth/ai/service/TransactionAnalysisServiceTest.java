package com.smartwealth.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartwealth.ai.domain.FinancialTransaction;
import com.smartwealth.ai.domain.TransactionType;
import com.smartwealth.ai.repository.FinancialTransactionRepository;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionAnalysisServiceTest {

    private TransactionAnalysisService transactionAnalysisService;
    private List<FinancialTransaction> transactions;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-14T00:00:00Z"), ZoneOffset.UTC);
        FinancialTransactionRepository repository = repositoryStub();
        transactionAnalysisService = new TransactionAnalysisService(repository, fixedClock);
    }

    @Test
    void shouldAnalyzeCurrentAndPreviousMonthCashflow() {
        transactions = List.of(
                tx(LocalDate.of(2026, 4, 5), TransactionType.INCOME, "Salary", "10000"),
                tx(LocalDate.of(2026, 4, 7), TransactionType.EXPENSE, "Housing", "3000"),
                tx(LocalDate.of(2026, 4, 8), TransactionType.EXPENSE, "Food", "1000"),
                tx(LocalDate.of(2026, 5, 5), TransactionType.INCOME, "Salary", "12000"),
                tx(LocalDate.of(2026, 5, 7), TransactionType.EXPENSE, "Housing", "3200"),
                tx(LocalDate.of(2026, 5, 8), TransactionType.EXPENSE, "Food", "1300")
        );

        var analyses = transactionAnalysisService.analyzeLastTwoMonths(1L);

        assertThat(analyses).hasSize(2);
        assertThat(analyses.getFirst().month()).isEqualTo(YearMonth.of(2026, 4));
        assertThat(analyses.getFirst().netCashflow()).isEqualByComparingTo("6000.00");
        assertThat(analyses.getLast().month()).isEqualTo(YearMonth.of(2026, 5));
        assertThat(analyses.getLast().netCashflow()).isEqualByComparingTo("7500.00");
        assertThat(analyses.getLast().expenseByCategory()).containsEntry("Housing", new BigDecimal("3200.00"));
    }

    private FinancialTransactionRepository repositoryStub() {
        return (FinancialTransactionRepository) Proxy.newProxyInstance(
                FinancialTransactionRepository.class.getClassLoader(),
                new Class[]{FinancialTransactionRepository.class},
                (proxy, method, args) -> {
                    if ("findByUserIdAndTransactionDateBetweenOrderByTransactionDateAsc".equals(method.getName())) {
                        LocalDate startDate = (LocalDate) args[1];
                        LocalDate endDate = (LocalDate) args[2];
                        return transactions.stream()
                                .filter(tx -> !tx.getTransactionDate().isBefore(startDate))
                                .filter(tx -> !tx.getTransactionDate().isAfter(endDate))
                                .sorted(Comparator.comparing(FinancialTransaction::getTransactionDate))
                                .toList();
                    }
                    throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private FinancialTransaction tx(LocalDate date, TransactionType type, String category, String amount) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setTransactionDate(date);
        transaction.setTransactionType(type);
        transaction.setCategory(category);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription(category);
        return transaction;
    }
}
