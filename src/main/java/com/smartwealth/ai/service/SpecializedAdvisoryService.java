package com.smartwealth.ai.service;

import com.smartwealth.ai.domain.FinancialProduct;
import com.smartwealth.ai.domain.ProductCategory;
import com.smartwealth.ai.domain.RiskLevel;
import com.smartwealth.ai.repository.FinancialProductRepository;
import com.smartwealth.ai.service.model.LlmProductSelection;
import com.smartwealth.ai.service.model.PortfolioAllocationSummary;
import com.smartwealth.ai.service.model.PortfolioHoldingSnapshot;
import com.smartwealth.ai.service.model.ProductRecommendation;
import com.smartwealth.ai.service.model.SpecializedAdvisoryResult;
import com.smartwealth.ai.service.model.SupportedLanguage;
import com.smartwealth.ai.service.model.WealthInsight;
import com.smartwealth.ai.service.model.WealthIntentCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SpecializedAdvisoryService {

    private static final Pattern MONEY_PATTERN = Pattern.compile("(\\d+(?:,\\d{3})*(?:\\.\\d+)?)");

    private final FinancialProductRepository financialProductRepository;
    private final PortfolioQueryService portfolioQueryService;

    public SpecializedAdvisoryService(
            FinancialProductRepository financialProductRepository,
            PortfolioQueryService portfolioQueryService
    ) {
        this.financialProductRepository = financialProductRepository;
        this.portfolioQueryService = portfolioQueryService;
    }

    public Optional<SpecializedAdvisoryResult> advise(WealthInsight insight, String userMessage) {
        WealthIntentCode code = insight.workflow().intentCode();
        return switch (code) {
            case FUND_SELECTION -> Optional.of(adviseFundSelection(insight, userMessage));
            case PRODUCT_COMPARISON -> Optional.of(adviseFixedDepositVsBond(insight));
            case PORTFOLIO_REBALANCING -> Optional.of(advisePortfolioRebalancing(insight));
            default -> Optional.empty();
        };
    }

    private SpecializedAdvisoryResult adviseFundSelection(WealthInsight insight, String userMessage) {
        Optional<BigDecimal> budget = extractBudget(userMessage);
        String preferredCurrency = detectCurrency(userMessage);
        List<ProductRecommendation> candidates = financialProductRepository
                .findBySupportedRiskLevelAndProductCategoryInOrderByAnnualReturnRateDesc(
                        insight.riskLevel(),
                        List.of(ProductCategory.FUND, ProductCategory.EQUITY_FUND, ProductCategory.MIXED_FUND)
                ).stream()
                .filter(product -> budget.map(value -> isBudgetEligible(product, value)).orElse(true))
                .sorted(Comparator
                        .comparing((FinancialProduct product) -> currencyScore(product.getCurrency(), preferredCurrency)).reversed()
                        .thenComparing(FinancialProduct::getAnnualReturnRate, Comparator.reverseOrder()))
                .limit(3)
                .map(product -> new ProductRecommendation(
                        product,
                        buildFundReason(product, budget.orElse(null), preferredCurrency, insight.language()),
                        false,
                        preferredCurrency
                ))
                .toList();
        Map<String, BigDecimal> allocationPlan = budget.map(value -> buildFundAllocationPlan(insight.riskLevel(), value, candidates)).orElse(Map.of());
        List<LlmProductSelection> finals = candidates.stream()
                .filter(item -> allocationPlan.isEmpty() || allocationPlan.containsKey(item.product().getProductCode()))
                .limit(allocationPlan.isEmpty() ? 2 : candidates.size())
                .map(item -> new LlmProductSelection(item, item.reason()))
                .toList();
        List<String> highlights = new ArrayList<>();
        if (insight.language() == SupportedLanguage.EN) {
            highlights.add(budget.isPresent()
                    ? "Filtered funds by your risk level, minimum investment threshold, and the stated budget."
                    : "Filtered funds by your risk level and product suitability because no explicit budget was provided.");
            highlights.add("Preferred funds in " + preferredCurrency + " where possible.");
            if (allocationPlan.isEmpty()) {
                highlights.add("No explicit budget was given, so the result uses a percentage-based allocation suggestion instead of fixed amounts.");
            } else {
                allocationPlan.forEach((productCode, amount) -> highlights.add("Suggested allocation: " + productCode + " -> " + amount.stripTrailingZeros().toPlainString() + " " + preferredCurrency));
            }
        } else {
            highlights.add(budget.isPresent()
                    ? "已按你的风险等级、起投门槛和预算金额筛选基金。"
                    : "由于你没有明确给出预算金额，当前先按风险等级和适配性筛选基金。");
            highlights.add("优先保留与问题币种更匹配的基金。");
            if (allocationPlan.isEmpty()) {
                highlights.add("当前返回的是比例型配置建议，而不是固定金额分配。");
            } else {
                allocationPlan.forEach((productCode, amount) -> highlights.add("建议分配： " + productCode + " -> " + amount.stripTrailingZeros().toPlainString() + " " + preferredCurrency));
            }
        }
        String summary = insight.language() == SupportedLanguage.EN
                ? (budget.isPresent()
                ? "Selected the best-fit fund candidates and proposed a budget allocation plan."
                : "Selected the best-fit fund candidates and proposed a percentage-based allocation.")
                : "已筛选出与预算最匹配的基金候选。";
        String answer = buildFundAnswer(insight.language(), budget.orElse(null), preferredCurrency, finals, allocationPlan);
        return new SpecializedAdvisoryResult(candidates, finals, List.of(), buildFundPlanSummaries(insight.language(), budget.orElse(null), preferredCurrency, finals, allocationPlan), highlights, summary, answer);
    }

    private SpecializedAdvisoryResult adviseFixedDepositVsBond(WealthInsight insight) {
        List<FinancialProduct> deposits = financialProductRepository.findBySupportedRiskLevelAndProductCategoryInOrderByAnnualReturnRateDesc(
                insight.riskLevel(), List.of(ProductCategory.FIXED_DEPOSIT)
        );
        List<FinancialProduct> bonds = financialProductRepository.findBySupportedRiskLevelAndProductCategoryInOrderByAnnualReturnRateDesc(
                insight.riskLevel(), List.of(ProductCategory.BOND)
        );
        FinancialProduct fixed = deposits.stream().findFirst().orElse(null);
        FinancialProduct bond = bonds.stream().findFirst().orElse(null);
        String preferredSide = preferredComparisonSide(insight.riskLevel(), insight.portfolioHoldings(), fixed, bond);
        List<ProductRecommendation> candidates = new ArrayList<>();
        deposits.stream().findFirst().ifPresent(product -> candidates.add(new ProductRecommendation(
                product,
                insight.language() == SupportedLanguage.EN
                        ? "Fixed deposit option with higher liquidity discipline and predictable capital preservation."
                        : "定存方案，强调本金稳定和确定性。", false, product.getCurrency()
        )));
        bonds.stream().findFirst().ifPresent(product -> candidates.add(new ProductRecommendation(
                product,
                insight.language() == SupportedLanguage.EN
                        ? "Bond option with better yield potential but more interest-rate and credit sensitivity."
                        : "债券方案，收益潜力更高，但利率和信用风险更高。", false, product.getCurrency()
        )));

        List<LlmProductSelection> finals = candidates.stream()
                .map(item -> new LlmProductSelection(item, item.reason()))
                .toList();
        List<String> highlights = new ArrayList<>();
        if (insight.language() == SupportedLanguage.EN) {
            highlights.add("Fixed deposits are better when principal stability and short-horizon certainty matter more.");
            highlights.add("Bonds are better when you can accept moderate mark-to-market volatility for extra yield.");
            highlights.add("Overall preference for your current profile: " + preferredSide + ".");
        } else {
            highlights.add("如果你更看重本金稳定和短期确定性，定存更合适。");
            highlights.add("如果你能接受一定波动来换取更高收益，债券更合适。");
            highlights.add("结合你当前画像，更偏向：" + ("fixed_deposit".equals(preferredSide) ? "定存" : "债券") + "。");
        }
        String summary = insight.language() == SupportedLanguage.EN
                ? "Compared a fixed-deposit option and a bond option under your current risk profile."
                : "已基于当前风险等级比较定存与债券方案。";
        String answer = buildFixedDepositVsBondAnswer(insight.language(), Optional.ofNullable(fixed), Optional.ofNullable(bond), preferredSide);
        return new SpecializedAdvisoryResult(candidates, finals, List.of(), List.of(), highlights, summary, answer);
    }

    private SpecializedAdvisoryResult advisePortfolioRebalancing(WealthInsight insight) {
        List<PortfolioHoldingSnapshot> holdings = insight.portfolioHoldings();
        List<PortfolioAllocationSummary> allocation = portfolioQueryService.allocationByCategory(insight.userId());
        BigDecimal totalValue = holdings.stream()
                .map(PortfolioHoldingSnapshot::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentEquityPercent = allocationPercentFor(allocation, ProductCategory.EQUITY_FUND, ProductCategory.FUND, ProductCategory.MIXED_FUND);
        BigDecimal currentBondPercent = allocationPercentFor(allocation, ProductCategory.BOND);
        BigDecimal currentDefensivePercent = allocationPercentFor(allocation, ProductCategory.CASH_MANAGEMENT, ProductCategory.FIXED_DEPOSIT);
        BigDecimal targetEquityPercent = volatileMarketTargetEquity(insight.riskLevel());
        BigDecimal targetBondPercent = volatileMarketTargetBond(insight.riskLevel());
        BigDecimal targetDefensivePercent = BigDecimal.valueOf(100).subtract(targetEquityPercent).subtract(targetBondPercent);
        BigDecimal shiftPercent = currentEquityPercent.subtract(targetEquityPercent).max(BigDecimal.ZERO).min(new BigDecimal("15.00"));
        BigDecimal suggestedShiftAmount = totalValue.multiply(shiftPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        ProductRecommendation defensiveCandidate = financialProductRepository
                .findBySupportedRiskLevelAndProductCategoryInOrderByAnnualReturnRateDesc(
                        fallbackRiskLevel(insight),
                        List.of(ProductCategory.BOND, ProductCategory.CASH_MANAGEMENT, ProductCategory.FIXED_DEPOSIT)
                ).stream()
                .findFirst()
                .map(product -> new ProductRecommendation(
                        product,
                        insight.language() == SupportedLanguage.EN
                                ? "Defensive sleeve candidate for lowering drawdown sensitivity in a volatile market."
                                : "用于降低组合波动的防御型候选资产。",
                        true,
                        product.getCurrency()
                ))
                .orElse(null);

        List<ProductRecommendation> candidates = defensiveCandidate == null ? List.of() : List.of(defensiveCandidate);
        List<LlmProductSelection> finals = defensiveCandidate == null ? List.of() : List.of(new LlmProductSelection(defensiveCandidate, defensiveCandidate.reason()));
        List<String> highlights = buildPortfolioHighlights(insight.language(), allocation, holdings);
        if (insight.language() == SupportedLanguage.EN) {
            highlights.add("Current equity allocation is about " + currentEquityPercent.stripTrailingZeros().toPlainString() + "%, versus a volatile-market target of about " + targetEquityPercent.stripTrailingZeros().toPlainString() + "%.");
            highlights.add("Suggested tactical shift amount is about " + suggestedShiftAmount.stripTrailingZeros().toPlainString() + ".");
        } else {
            highlights.add("当前权益类占比约 " + currentEquityPercent.stripTrailingZeros().toPlainString() + "%，而波动市场下更稳妥的权益目标占比约为 " + targetEquityPercent.stripTrailingZeros().toPlainString() + "%。");
            highlights.add("建议从最集中的权益仓位中，战术性腾挪约 " + suggestedShiftAmount.stripTrailingZeros().toPlainString() + " 的资金。");
        }
        String summary = insight.language() == SupportedLanguage.EN
                ? "Reviewed current allocation and identified where to reduce volatility concentration."
                : "已检查当前持仓结构并识别组合中的高波动集中项。";
        String answer = buildPortfolioAnswer(
                insight.language(),
                allocation,
                holdings,
                defensiveCandidate,
                currentEquityPercent,
                currentBondPercent,
                currentDefensivePercent,
                targetEquityPercent,
                targetDefensivePercent,
                suggestedShiftAmount
        );
        return new SpecializedAdvisoryResult(candidates, finals, List.of(), List.of(), highlights, summary, answer);
    }

    private List<String> buildPortfolioHighlights(
            SupportedLanguage language,
            List<PortfolioAllocationSummary> allocation,
            List<PortfolioHoldingSnapshot> holdings
    ) {
        List<String> result = new ArrayList<>();
        PortfolioAllocationSummary top = allocation.isEmpty() ? null : allocation.getFirst();
        PortfolioHoldingSnapshot topHolding = holdings.isEmpty() ? null : holdings.getFirst();
        if (language == SupportedLanguage.EN) {
            if (top != null) {
                result.add("Largest category exposure is " + top.category() + " at about " + top.allocationPercent().stripTrailingZeros().toPlainString() + "%.");
            }
            if (topHolding != null) {
                result.add("Most concentrated single holding is " + topHolding.productName() + " at about " + topHolding.allocationPercent().stripTrailingZeros().toPlainString() + "%.");
            }
            result.add("In volatile markets, reduce concentration before adding new high-beta exposure.");
        } else {
            if (top != null) {
                result.add("当前最大类别敞口为 " + top.category() + "，占比约 " + top.allocationPercent().stripTrailingZeros().toPlainString() + "%。");
            }
            if (topHolding != null) {
                result.add("当前最集中的单一持仓为 " + topHolding.productName() + "，占比约 " + topHolding.allocationPercent().stripTrailingZeros().toPlainString() + "%。");
            }
            result.add("市场波动较大时，应先降低集中度，再考虑增加高波动资产。");
        }
        return result;
    }

    private String buildFundAnswer(
            SupportedLanguage language,
            BigDecimal budget,
            String currency,
            List<LlmProductSelection> finals,
            Map<String, BigDecimal> allocationPlan
    ) {
        if (finals.isEmpty()) {
            return language == SupportedLanguage.EN
                    ? "No fund in the current product universe fits the stated budget and risk profile."
                    : "当前产品池中没有同时满足预算和风险等级的基金。";
        }
        String joined = finals.stream()
                .map(item -> "%s (%s)".formatted(item.product().product().getProductName(), item.product().product().getProductCode()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        String allocationText = finals.stream()
                .map(item -> item.product().product().getProductCode())
                .filter(allocationPlan::containsKey)
                .map(code -> code + " -> " + allocationPlan.get(code).stripTrailingZeros().toPlainString() + " " + currency)
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
        String percentageText = switch (finals.size()) {
            case 0 -> "";
            case 1 -> finals.get(0).product().product().getProductCode() + " -> 100%";
            case 2 -> finals.get(0).product().product().getProductCode() + " -> 60%; " + finals.get(1).product().product().getProductCode() + " -> 40%";
            default -> finals.get(0).product().product().getProductCode() + " -> 50%; "
                    + finals.get(1).product().product().getProductCode() + " -> 30%; "
                    + finals.get(2).product().product().getProductCode() + " -> 20%";
        };
        if (budget == null) {
            return language == SupportedLanguage.EN
                    ? "The top fund candidates for your profile are %s. Since you did not specify a budget, I am giving a percentage-based allocation instead of fixed amounts: %s. Prioritize the first fund as the core allocation and use the remaining fund(s) for diversification."
                    .formatted(joined, percentageText)
                    : "结合你的风险画像，当前更适合的基金候选为 %s。由于你没有明确给出预算金额，我先给出比例型配置建议而不是固定金额：%s。建议将首个基金作为核心仓位，其余基金用于分散配置。"
                    .formatted(joined, percentageText);
        }
        return language == SupportedLanguage.EN
                ? "For a budget of %s %s, the strongest current fund candidates are %s. They pass the minimum investment threshold and are aligned with your current risk profile. A practical allocation plan is: %s. Prioritize the first candidate for the core allocation, and use the later candidates for diversification."
                .formatted(budget.stripTrailingZeros().toPlainString(), currency, joined, allocationText)
                : "针对 %s %s 的预算，当前更匹配的基金候选为 %s。这些产品满足起投门槛，并与你当前的风险等级匹配。一个更实用的分配方案是：%s。建议将首个候选作为核心仓位，后续候选用于分散配置。"
                .formatted(budget.stripTrailingZeros().toPlainString(), currency, joined, allocationText);
    }

    private List<String> buildFundPlanSummaries(
            SupportedLanguage language,
            BigDecimal budget,
            String currency,
            List<LlmProductSelection> finals,
            Map<String, BigDecimal> allocationPlan
    ) {
        if (budget == null) {
            if (finals.isEmpty()) {
                return List.of();
            }
            return switch (finals.size()) {
                case 1 -> List.of(finals.get(0).product().product().getProductName()
                        + (language == SupportedLanguage.EN ? ": 100% core allocation" : "：100% 核心仓位"));
                case 2 -> List.of(
                        finals.get(0).product().product().getProductName() + (language == SupportedLanguage.EN ? ": 60% core allocation" : "：60% 核心仓位"),
                        finals.get(1).product().product().getProductName() + (language == SupportedLanguage.EN ? ": 40% diversification allocation" : "：40% 分散配置")
                );
                default -> List.of(
                        finals.get(0).product().product().getProductName() + (language == SupportedLanguage.EN ? ": 50% core allocation" : "：50% 核心仓位"),
                        finals.get(1).product().product().getProductName() + (language == SupportedLanguage.EN ? ": 30% secondary allocation" : "：30% 次级配置"),
                        finals.get(2).product().product().getProductName() + (language == SupportedLanguage.EN ? ": 20% diversification allocation" : "：20% 分散配置")
                );
            };
        }
        return finals.stream()
                .map(item -> {
                    BigDecimal amount = allocationPlan.get(item.product().product().getProductCode());
                    if (amount == null) {
                        amount = budget.divide(BigDecimal.valueOf(Math.max(finals.size(), 1)), 2, RoundingMode.HALF_UP);
                    }
                    return language == SupportedLanguage.EN
                            ? "%s: allocate about %s %s".formatted(item.product().product().getProductName(), amount.stripTrailingZeros().toPlainString(), currency)
                            : "%s：建议分配约 %s %s".formatted(item.product().product().getProductName(), amount.stripTrailingZeros().toPlainString(), currency);
                })
                .toList();
    }

    private String buildFixedDepositVsBondAnswer(
            SupportedLanguage language,
            Optional<FinancialProduct> deposit,
            Optional<FinancialProduct> bond,
            String preferredSide
    ) {
        if (deposit.isEmpty() || bond.isEmpty()) {
            return language == SupportedLanguage.EN
                    ? "The current product universe does not contain both a fixed-deposit and a bond option for a clean comparison."
                    : "当前产品池中缺少可直接对比的定存和债券产品。";
        }
        FinancialProduct fixed = deposit.get();
        FinancialProduct debt = bond.get();
        return language == SupportedLanguage.EN
                ? """
                For your current risk profile, fixed deposits are better when capital stability, shorter decision cycles, and predictable redemption matter most. Bonds are better when you can tolerate moderate duration risk to earn higher yield.

                In the current product set, %s offers about %s annualized return with %d days minimum holding, while %s offers about %s annualized return with %d days minimum holding.
                For you right now, the slightly better fit is %s. If your priority is certainty and simpler cash planning, choose the fixed-deposit side first. If your priority is slightly better return and you can hold through rate volatility, the bond side is more suitable.
                """.formatted(
                        fixed.getProductName(),
                        toPercent(fixed.getAnnualReturnRate()),
                        fixed.getMinHoldingDays(),
                        debt.getProductName(),
                        toPercent(debt.getAnnualReturnRate()),
                        debt.getMinHoldingDays(),
                        "fixed_deposit".equals(preferredSide) ? fixed.getProductName() : debt.getProductName()
                ).trim()
                : """
                对你当前的风险等级来说，如果你更看重本金稳定、决策周期短、现金安排更确定，定存更合适；如果你能接受一定久期波动来换取更高收益，债券更合适。

                在当前产品池中，%s 年化约 %s、最短持有 %d 天；%s 年化约 %s、最短持有 %d 天。
                结合你当前画像，现阶段更适合你的选项是 %s。如果你优先考虑确定性和更简单的资金规划，优先选定存；如果你优先考虑略高收益并能承受利率波动，则债券更适合。
                """.formatted(
                        fixed.getProductName(),
                        toPercent(fixed.getAnnualReturnRate()),
                        fixed.getMinHoldingDays(),
                        debt.getProductName(),
                        toPercent(debt.getAnnualReturnRate()),
                        debt.getMinHoldingDays(),
                        "fixed_deposit".equals(preferredSide) ? fixed.getProductName() : debt.getProductName()
                ).trim();
    }

    private String buildPortfolioAnswer(
            SupportedLanguage language,
            List<PortfolioAllocationSummary> allocation,
            List<PortfolioHoldingSnapshot> holdings,
            ProductRecommendation defensiveCandidate,
            BigDecimal currentEquityPercent,
            BigDecimal currentBondPercent,
            BigDecimal currentDefensivePercent,
            BigDecimal targetEquityPercent,
            BigDecimal targetDefensivePercent,
            BigDecimal suggestedShiftAmount
    ) {
        PortfolioAllocationSummary topCategory = allocation.isEmpty() ? null : allocation.getFirst();
        PortfolioHoldingSnapshot topHolding = holdings.isEmpty() ? null : holdings.getFirst();
        if (language == SupportedLanguage.EN) {
            String base = "The first adjustment in a volatile market should be reducing concentration risk, not blindly adding new growth exposure.";
            if (topCategory != null && topHolding != null) {
                base += " Your largest category exposure is %s at %s%%, and your largest single holding is %s at %s%%."
                        .formatted(
                                topCategory.category(),
                                topCategory.allocationPercent().stripTrailingZeros().toPlainString(),
                                topHolding.productName(),
                                topHolding.allocationPercent().stripTrailingZeros().toPlainString()
                        );
            }
            base += " Your portfolio is currently about %s%% equity, %s%% bond, and %s%% defensive cash/fixed-income."
                    .formatted(
                            currentEquityPercent.stripTrailingZeros().toPlainString(),
                            currentBondPercent.stripTrailingZeros().toPlainString(),
                            currentDefensivePercent.stripTrailingZeros().toPlainString()
                    );
            base += " In a volatile market, a more defensive tactical mix would bring equity closer to %s%% and defensive assets closer to %s%%."
                    .formatted(
                            targetEquityPercent.stripTrailingZeros().toPlainString(),
                            targetDefensivePercent.stripTrailingZeros().toPlainString()
                    );
            if (defensiveCandidate != null) {
                base += " A practical adjustment is to trim about %s from the most concentrated equity sleeve and redirect that capital into %s as a more defensive stabilizer."
                        .formatted(suggestedShiftAmount.stripTrailingZeros().toPlainString(), defensiveCandidate.product().getProductName());
            }
            return base;
        }
        String base = "市场波动较大时，第一步应优先降低集中度，而不是继续盲目增加高成长暴露。";
        if (topCategory != null && topHolding != null) {
            base += " 当前最大类别敞口为 %s，占比 %s%%；最大单一持仓为 %s，占比 %s%%。"
                    .formatted(
                            topCategory.category(),
                            topCategory.allocationPercent().stripTrailingZeros().toPlainString(),
                            topHolding.productName(),
                            topHolding.allocationPercent().stripTrailingZeros().toPlainString()
                    );
        }
        base += " 你当前组合大致为：权益 %s%%、债券 %s%%、防御型现金/定存 %s%%。"
                .formatted(
                        currentEquityPercent.stripTrailingZeros().toPlainString(),
                        currentBondPercent.stripTrailingZeros().toPlainString(),
                        currentDefensivePercent.stripTrailingZeros().toPlainString()
                );
        base += " 在高波动环境下，更稳妥的战术目标是把权益仓位逐步降到约 %s%%，同时把防御资产提高到约 %s%%。"
                .formatted(
                        targetEquityPercent.stripTrailingZeros().toPlainString(),
                        targetDefensivePercent.stripTrailingZeros().toPlainString()
                );
        if (defensiveCandidate != null) {
            base += " 一个更稳妥的调整方式，是从最集中的权益仓位中腾挪约 %s 的资金，并将其转向 %s 这类防御型资产。"
                    .formatted(suggestedShiftAmount.stripTrailingZeros().toPlainString(), defensiveCandidate.product().getProductName());
        }
        return base;
    }

    private String buildFundReason(FinancialProduct product, BigDecimal budget, String currency, SupportedLanguage language) {
        if (budget == null) {
            if (language == SupportedLanguage.EN) {
                return "Aligned with your risk profile, available in %s, and competitive on return within the current fund universe.".formatted(currency);
            }
            return "符合你的风险等级，币种为 %s，并且在当前基金池中具备较强收益竞争力。".formatted(currency);
        }
        if (language == SupportedLanguage.EN) {
            return "Eligible under the stated budget of %s %s, aligned with your risk profile, and competitive on return within the current fund universe."
                    .formatted(budget.stripTrailingZeros().toPlainString(), currency);
        }
        return "满足当前预算 %s %s，符合你的风险等级，并且在现有基金池中具备较强收益竞争力。"
                .formatted(budget.stripTrailingZeros().toPlainString(), currency);
    }

    private boolean isBudgetEligible(FinancialProduct product, BigDecimal budget) {
        return product.getMinimumInvestmentAmount() != null
                && budget.compareTo(product.getMinimumInvestmentAmount()) >= 0;
    }

    private int currencyScore(String productCurrency, String preferredCurrency) {
        if (preferredCurrency == null || preferredCurrency.isBlank()) {
            return 1;
        }
        return preferredCurrency.equalsIgnoreCase(productCurrency) ? 2 : 1;
    }

    private Optional<BigDecimal> extractBudget(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = MONEY_PATTERN.matcher(message.replace(",", ""));
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(matcher.group(1)).setScale(2, RoundingMode.HALF_UP));
    }

    private RiskLevel fallbackRiskLevel(WealthInsight insight) {
        return switch (insight.riskLevel()) {
            case AGGRESSIVE -> RiskLevel.MODERATE;
            case GROWTH -> RiskLevel.MODERATE;
            case MODERATE, BALANCED -> RiskLevel.CONSERVATIVE;
            case CONSERVATIVE -> RiskLevel.CONSERVATIVE;
        };
    }

    private Map<String, BigDecimal> buildFundAllocationPlan(
            RiskLevel riskLevel,
            BigDecimal budget,
            List<ProductRecommendation> candidates
    ) {
        List<BigDecimal> ratios = switch (riskLevel) {
            case CONSERVATIVE -> List.of(new BigDecimal("0.60"), new BigDecimal("0.40"));
            case MODERATE, BALANCED -> List.of(new BigDecimal("0.50"), new BigDecimal("0.30"), new BigDecimal("0.20"));
            case GROWTH, AGGRESSIVE -> List.of(new BigDecimal("0.45"), new BigDecimal("0.35"), new BigDecimal("0.20"));
        };
        Map<String, BigDecimal> plan = new LinkedHashMap<>();
        BigDecimal remaining = budget.setScale(2, RoundingMode.HALF_UP);
        int plannedCount = Math.min(candidates.size(), ratios.size());
        for (int index = 0; index < plannedCount; index++) {
            ProductRecommendation item = candidates.get(index);
            BigDecimal amount = index == plannedCount - 1
                    ? remaining
                    : budget.multiply(ratios.get(index)).setScale(2, RoundingMode.HALF_UP);
            amount = amount.max(item.product().getMinimumInvestmentAmount());
            if (amount.compareTo(remaining) > 0) {
                amount = remaining;
            }
            if (amount.signum() > 0) {
                plan.put(item.product().getProductCode(), amount);
                remaining = remaining.subtract(amount).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return plan;
    }

    private String preferredComparisonSide(
            RiskLevel riskLevel,
            List<PortfolioHoldingSnapshot> holdings,
            FinancialProduct fixed,
            FinancialProduct bond
    ) {
        BigDecimal bondAllocation = holdings.stream()
                .filter(item -> item.productCategory() == ProductCategory.BOND)
                .map(PortfolioHoldingSnapshot::allocationPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fixedAllocation = holdings.stream()
                .filter(item -> item.productCategory() == ProductCategory.FIXED_DEPOSIT || item.productCategory() == ProductCategory.CASH_MANAGEMENT)
                .map(PortfolioHoldingSnapshot::allocationPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (riskLevel == RiskLevel.CONSERVATIVE) {
            return bondAllocation.compareTo(new BigDecimal("30")) > 0 ? "fixed_deposit" : "bond";
        }
        if (fixedAllocation.compareTo(new BigDecimal("25")) > 0) {
            return "bond";
        }
        if (bond != null && fixed != null && bond.getAnnualReturnRate().subtract(fixed.getAnnualReturnRate()).compareTo(new BigDecimal("0.0030")) > 0) {
            return "bond";
        }
        return "fixed_deposit";
    }

    private String detectCurrency(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        if (normalized.contains("$") || normalized.contains("usd")) {
            return "USD";
        }
        if (normalized.contains("sgd")) {
            return "SGD";
        }
        return "SGD";
    }

    private BigDecimal allocationPercentFor(List<PortfolioAllocationSummary> allocation, ProductCategory... categories) {
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioAllocationSummary item : allocation) {
            for (ProductCategory category : categories) {
                if (item.category() == category) {
                    total = total.add(item.allocationPercent());
                    break;
                }
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal volatileMarketTargetEquity(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case CONSERVATIVE -> new BigDecimal("15.00");
            case MODERATE, BALANCED -> new BigDecimal("45.00");
            case GROWTH -> new BigDecimal("60.00");
            case AGGRESSIVE -> new BigDecimal("65.00");
        };
    }

    private BigDecimal volatileMarketTargetBond(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case CONSERVATIVE -> new BigDecimal("45.00");
            case MODERATE, BALANCED -> new BigDecimal("30.00");
            case GROWTH -> new BigDecimal("25.00");
            case AGGRESSIVE -> new BigDecimal("20.00");
        };
    }

    private String toPercent(BigDecimal rate) {
        return rate.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
}
