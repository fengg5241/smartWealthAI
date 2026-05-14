package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.CategorySpend;
import com.smartwealth.ai.api.response.MonthlyCashflowSummary;
import com.smartwealth.ai.domain.FinancialTransaction;
import com.smartwealth.ai.domain.TransactionType;
import com.smartwealth.ai.repository.FinancialTransactionRepository;
import com.smartwealth.ai.service.model.MonthlyAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TransactionAnalysisService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final FinancialTransactionRepository transactionRepository;
    private final Clock clock;

    public TransactionAnalysisService(FinancialTransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    public List<MonthlyAnalysis> analyzeLastTwoMonths(Long userId) {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        YearMonth previousMonth = currentMonth.minusMonths(1);
        LocalDate startDate = previousMonth.atDay(1);
        LocalDate endDate = currentMonth.atEndOfMonth();

        List<FinancialTransaction> transactions =
                transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateAsc(userId, startDate, endDate);

        Map<YearMonth, List<FinancialTransaction>> grouped = transactions.stream()
                .collect(Collectors.groupingBy(tx -> YearMonth.from(tx.getTransactionDate())));

        List<MonthlyAnalysis> analyses = new ArrayList<>();
        analyses.add(analyzeMonth(previousMonth, grouped.getOrDefault(previousMonth, List.of())));
        analyses.add(analyzeMonth(currentMonth, grouped.getOrDefault(currentMonth, List.of())));
        return analyses;
    }

    private MonthlyAnalysis analyzeMonth(YearMonth month, List<FinancialTransaction> monthTransactions) {
        BigDecimal income = monthTransactions.stream()
                .filter(tx -> tx.getTransactionType() == TransactionType.INCOME)
                .map(FinancialTransaction::getAmount)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal expense = monthTransactions.stream()
                .filter(tx -> tx.getTransactionType() == TransactionType.EXPENSE)
                .map(FinancialTransaction::getAmount)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netCashflow = income.subtract(expense).setScale(2, RoundingMode.HALF_UP);
        BigDecimal savingsRate = income.signum() == 0
                ? ZERO
                : netCashflow.divide(income, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> expenseByCategory = monthTransactions.stream()
                .filter(tx -> tx.getTransactionType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        FinancialTransaction::getCategory,
                        Collectors.mapping(FinancialTransaction::getAmount,
                                Collectors.reducing(ZERO, BigDecimal::add))
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().setScale(2, RoundingMode.HALF_UP),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return new MonthlyAnalysis(month, income, expense, netCashflow, savingsRate, expenseByCategory);
    }

    public MonthlyCashflowSummary toSummary(MonthlyAnalysis analysis) {
        BigDecimal totalExpense = analysis.expense();
        List<CategorySpend> breakdown = analysis.expenseByCategory().entrySet().stream()
                .map(entry -> new CategorySpend(
                        entry.getKey(),
                        entry.getValue(),
                        totalExpense.signum() == 0
                                ? ZERO
                                : entry.getValue().divide(totalExpense, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();

        return new MonthlyCashflowSummary(
                analysis.month(),
                analysis.income(),
                analysis.expense(),
                analysis.netCashflow(),
                analysis.savingsRate(),
                breakdown
        );
    }
}
