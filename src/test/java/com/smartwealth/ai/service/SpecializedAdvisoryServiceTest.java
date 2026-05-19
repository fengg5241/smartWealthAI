package com.smartwealth.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartwealth.ai.api.response.ChatIntentType;
import com.smartwealth.ai.domain.FinancialProduct;
import com.smartwealth.ai.domain.LiquidityLevel;
import com.smartwealth.ai.domain.ProductCategory;
import com.smartwealth.ai.domain.RiskLevel;
import com.smartwealth.ai.repository.FinancialProductRepository;
import com.smartwealth.ai.service.model.GoalProjection;
import com.smartwealth.ai.service.model.GoalScenarioAnalysis;
import com.smartwealth.ai.service.model.MonthlyAnalysis;
import com.smartwealth.ai.service.model.PortfolioAllocationSummary;
import com.smartwealth.ai.service.model.PortfolioHoldingSnapshot;
import com.smartwealth.ai.service.model.ResponsePolicy;
import com.smartwealth.ai.service.model.SupportedLanguage;
import com.smartwealth.ai.service.model.WealthInsight;
import com.smartwealth.ai.service.model.WealthIntentCode;
import com.smartwealth.ai.service.model.WealthWorkflow;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpecializedAdvisoryServiceTest {

    private SpecializedAdvisoryService specializedAdvisoryService;

    @BeforeEach
    void setUp() {
        FinancialProductRepository financialProductRepository = financialProductRepositoryStub();
        PortfolioQueryService portfolioQueryService = portfolioQueryServiceStub();
        specializedAdvisoryService = new SpecializedAdvisoryService(financialProductRepository, portfolioQueryService);
    }

    @Test
    void shouldBuildFundSelectionAllocationPlan() {
        WealthInsight insight = insight(
                WealthIntentCode.FUND_SELECTION,
                RiskLevel.MODERATE,
                List.of(),
                List.of()
        );

        var result = specializedAdvisoryService.advise(insight, "What is the best funds for $50,000?").orElseThrow();

        assertThat(result.finalRecommendations()).hasSize(3);
        assertThat(result.answer()).contains("50000", "USD");
        assertThat(result.answer()).contains("SG9999013486");
        assertThat(result.answer()).contains("USD");
        assertThat(result.advisoryHighlights()).anyMatch(item -> item.contains("Suggested allocation"));
    }

    @Test
    void shouldUsePercentageAllocationWhenBudgetIsNotProvided() {
        WealthInsight insight = insight(
                WealthIntentCode.FUND_SELECTION,
                RiskLevel.MODERATE,
                List.of(),
                List.of()
        );

        var result = specializedAdvisoryService.advise(insight, "What is the best funds for me").orElseThrow();

        assertThat(result.answer()).doesNotContain("50000");
        assertThat(result.answer()).contains("percentage-based allocation");
        assertThat(result.investmentPlanSummaries()).isNotEmpty();
    }

    @Test
    void shouldPreferBondInFixedDepositVsBondComparisonWhenFixedDepositAlreadyHigh() {
        WealthInsight insight = insight(
                WealthIntentCode.PRODUCT_COMPARISON,
                RiskLevel.MODERATE,
                List.of(
                        new PortfolioHoldingSnapshot("CON-003", "Fixed Deposit Reserve", ProductCategory.FIXED_DEPOSIT, "SGD",
                                new BigDecimal("8000"), new BigDecimal("8160"), new BigDecimal("30.00"), LocalDate.now().minusMonths(5))
                ),
                List.of(
                        new PortfolioAllocationSummary(ProductCategory.FIXED_DEPOSIT, new BigDecimal("30.00"), new BigDecimal("8160.00")),
                        new PortfolioAllocationSummary(ProductCategory.BOND, new BigDecimal("18.00"), new BigDecimal("9180.00"))
                )
        );

        var result = specializedAdvisoryService.advise(insight, "Fixed deposits or bonds is better for me?").orElseThrow();

        assertThat(result.answer()).contains("bond");
        assertThat(result.advisoryHighlights()).anyMatch(item -> item.contains("bond"));
        assertThat(result.candidateProducts()).hasSize(2);
    }

    @Test
    void shouldProvideTargetMixAndShiftAmountForPortfolioRebalancing() {
        WealthInsight insight = insight(
                WealthIntentCode.PORTFOLIO_REBALANCING,
                RiskLevel.AGGRESSIVE,
                List.of(
                        new PortfolioHoldingSnapshot("LU0548575426", "Emerging Markets Growth", ProductCategory.EQUITY_FUND, "USD",
                                new BigDecimal("26000"), new BigDecimal("30100"), new BigDecimal("34.00"), LocalDate.now().minusMonths(20)),
                        new PortfolioHoldingSnapshot("SG9999000251", "EM Dividend Allocation", ProductCategory.EQUITY_FUND, "SGD",
                                new BigDecimal("18000"), new BigDecimal("20150"), new BigDecimal("23.00"), LocalDate.now().minusMonths(15)),
                        new PortfolioHoldingSnapshot("CON-002", "Bond Shock Absorber", ProductCategory.BOND, "SGD",
                                new BigDecimal("12000"), new BigDecimal("12160"), new BigDecimal("14.00"), LocalDate.now().minusMonths(7)),
                        new PortfolioHoldingSnapshot("CON-001", "Liquidity Parking", ProductCategory.CASH_MANAGEMENT, "SGD",
                                new BigDecimal("9000"), new BigDecimal("9135"), new BigDecimal("12.00"), LocalDate.now().minusMonths(4))
                ),
                List.of(
                        new PortfolioAllocationSummary(ProductCategory.EQUITY_FUND, new BigDecimal("57.00"), new BigDecimal("50250.00")),
                        new PortfolioAllocationSummary(ProductCategory.BOND, new BigDecimal("14.00"), new BigDecimal("12160.00")),
                        new PortfolioAllocationSummary(ProductCategory.CASH_MANAGEMENT, new BigDecimal("12.00"), new BigDecimal("9135.00"))
                )
        );

        var result = specializedAdvisoryService.advise(insight, "The market is so volatile now, how should I adjust my portfolio?").orElseThrow();

        assertThat(result.answer()).contains("equity");
        assertThat(result.answer()).contains("65");
        assertThat(result.answer()).contains("defensive");
        assertThat(result.advisoryHighlights()).anyMatch(item -> item.contains("Suggested tactical shift amount"));
    }

    private PortfolioQueryService portfolioQueryServiceStub() {
        return new PortfolioQueryService(null) {
            @Override
            public List<PortfolioHoldingSnapshot> holdings(Long userId) {
                return stubHoldings;
            }

            @Override
            public List<PortfolioAllocationSummary> allocationByCategory(Long userId) {
                return stubAllocations;
            }
        };
    }

    private List<PortfolioHoldingSnapshot> stubHoldings = List.of();
    private List<PortfolioAllocationSummary> stubAllocations = List.of();

    private WealthInsight insight(
            WealthIntentCode intentCode,
            RiskLevel riskLevel,
            List<PortfolioHoldingSnapshot> holdings,
            List<PortfolioAllocationSummary> allocations
    ) {
        this.stubHoldings = holdings;
        this.stubAllocations = allocations;
        return new WealthInsight(
                1L,
                SupportedLanguage.EN,
                new WealthWorkflow(ChatIntentType.WEALTH_ADVISORY, intentCode, intentCode.name(), "test", ResponsePolicy.SPECIALIZED_EXECUTE, false, false, false, true),
                riskLevel,
                List.of(new MonthlyAnalysis(
                        YearMonth.of(2026, 5),
                        new BigDecimal("18000"),
                        new BigDecimal("9000"),
                        new BigDecimal("9000"),
                        new BigDecimal("50.00"),
                        Map.of("Housing", new BigDecimal("5200.00"))
                )),
                new GoalProjection(
                        "Goal",
                        new BigDecimal("100000"),
                        LocalDate.of(2027, 5, 17),
                        new BigDecimal("8000"),
                        new BigDecimal("8000"),
                        12,
                        LocalDate.of(2027, 5, 17),
                        true
                ),
                new GoalScenarioAnalysis(
                        "Goal",
                        new BigDecimal("100000"),
                        new BigDecimal("100000"),
                        new BigDecimal("20000"),
                        new BigDecimal("80000"),
                        false,
                        10,
                        LocalDate.of(2027, 3, 17),
                        "SGD"
                ),
                false,
                List.of(),
                List.of(),
                holdings,
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private FinancialProductRepository financialProductRepositoryStub() {
        return (FinancialProductRepository) Proxy.newProxyInstance(
                FinancialProductRepository.class.getClassLoader(),
                new Class[]{FinancialProductRepository.class},
                (proxy, method, args) -> {
                    if ("findBySupportedRiskLevelAndProductCategoryInOrderByAnnualReturnRateDesc".equals(method.getName())) {
                        RiskLevel riskLevel = (RiskLevel) args[0];
                        @SuppressWarnings("unchecked")
                        List<ProductCategory> categories = (List<ProductCategory>) args[1];
                        return products().stream()
                                .filter(product -> product.getSupportedRiskLevel() == riskLevel)
                                .filter(product -> categories.contains(product.getProductCategory()))
                                .sorted((left, right) -> right.getAnnualReturnRate().compareTo(left.getAnnualReturnRate()))
                                .toList();
                    }
                    if ("findByProductCategoryInOrderByAnnualReturnRateDesc".equals(method.getName())) {
                        @SuppressWarnings("unchecked")
                        List<ProductCategory> categories = (List<ProductCategory>) args[0];
                        return products().stream()
                                .filter(product -> categories.contains(product.getProductCategory()))
                                .sorted((left, right) -> right.getAnnualReturnRate().compareTo(left.getAnnualReturnRate()))
                                .toList();
                    }
                    throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private List<FinancialProduct> products() {
        return List.of(
                product("CON-001", "Cash Shield Income", ProductCategory.CASH_MANAGEMENT, RiskLevel.CONSERVATIVE, "SGD", "1000", "0.0280", 30, LiquidityLevel.HIGH),
                product("CON-002", "Stable Bond Ladder", ProductCategory.BOND, RiskLevel.CONSERVATIVE, "SGD", "5000", "0.0320", 120, LiquidityLevel.MEDIUM),
                product("CON-003", "Treasury Plus Plan", ProductCategory.FIXED_DEPOSIT, RiskLevel.CONSERVATIVE, "SGD", "10000", "0.0300", 90, LiquidityLevel.HIGH),
                product("CON-FND-001", "Prudent Income Fund SGD", ProductCategory.FUND, RiskLevel.CONSERVATIVE, "SGD", "1000", "0.0345", 30, LiquidityLevel.HIGH),
                product("CON-FND-002", "Prudent Income Fund USD", ProductCategory.FUND, RiskLevel.CONSERVATIVE, "USD", "1000", "0.0340", 30, LiquidityLevel.HIGH),
                product("CON-FND-003", "Capital Stable Multi-Asset Fund SGD", ProductCategory.MIXED_FUND, RiskLevel.CONSERVATIVE, "SGD", "1000", "0.0405", 120, LiquidityLevel.MEDIUM),
                product("SG9999013486", "LIONGLOBAL SINGAPORE DIVIDEND EQUITY FUND USD-H", ProductCategory.EQUITY_FUND, RiskLevel.MODERATE, "USD", "1000", "0.5124", 90, LiquidityLevel.HIGH),
                product("SG9999013478", "LIONGLOBAL SINGAPORE DIVIDEND EQUITY FUND USD", ProductCategory.EQUITY_FUND, RiskLevel.MODERATE, "USD", "1000", "0.5085", 90, LiquidityLevel.HIGH),
                product("SG9999013460", "LIONGLOBAL SINGAPORE DIVIDEND EQUITY FUND SGD", ProductCategory.EQUITY_FUND, RiskLevel.MODERATE, "SGD", "1000", "0.4714", 90, LiquidityLevel.HIGH),
                product("LU0548575426", "FIDELITY EMERGING MARKETS FUND A USD", ProductCategory.EQUITY_FUND, RiskLevel.AGGRESSIVE, "USD", "1000", "0.6318", 90, LiquidityLevel.MEDIUM),
                product("SG9999000251", "SCHRODER EMERGING MARKETS FUND SGD", ProductCategory.EQUITY_FUND, RiskLevel.AGGRESSIVE, "SGD", "1000", "0.5656", 90, LiquidityLevel.MEDIUM),
                product("CON-002-M", "Moderate Bond Ladder", ProductCategory.BOND, RiskLevel.MODERATE, "SGD", "5000", "0.0380", 120, LiquidityLevel.MEDIUM),
                product("CON-003-M", "Moderate Fixed Deposit", ProductCategory.FIXED_DEPOSIT, RiskLevel.MODERATE, "SGD", "10000", "0.0310", 90, LiquidityLevel.HIGH),
                product("CON-004-M", "Moderate Cash Buffer", ProductCategory.CASH_MANAGEMENT, RiskLevel.MODERATE, "SGD", "1000", "0.0270", 30, LiquidityLevel.HIGH)
        );
    }

    private FinancialProduct product(
            String code,
            String name,
            ProductCategory category,
            RiskLevel riskLevel,
            String currency,
            String minAmount,
            String annualReturn,
            int minHoldingDays,
            LiquidityLevel liquidityLevel
    ) {
        FinancialProduct product = new FinancialProduct();
        product.setProductCode(code);
        product.setProductName(name);
        product.setProductCategory(category);
        product.setSupportedRiskLevel(riskLevel);
        product.setCurrency(currency);
        product.setMinimumInvestmentAmount(new BigDecimal(minAmount));
        product.setAnnualReturnRate(new BigDecimal(annualReturn));
        product.setMinHoldingDays(minHoldingDays);
        product.setLiquidityLevel(liquidityLevel);
        product.setDescription(name);
        product.setComplianceNote("Compliance");
        return product;
    }
}
