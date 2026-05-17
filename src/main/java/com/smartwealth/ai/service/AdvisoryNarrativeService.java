package com.smartwealth.ai.service;

import com.smartwealth.ai.service.model.LlmProductSelection;
import com.smartwealth.ai.service.model.GoalProjection;
import com.smartwealth.ai.service.model.MonthlyAnalysis;
import com.smartwealth.ai.service.model.ProductRecommendation;
import com.smartwealth.ai.service.model.InvestmentPlan;
import com.smartwealth.ai.service.model.SupportedLanguage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdvisoryNarrativeService {

    public List<String> buildHighlights(
            SupportedLanguage language,
            List<MonthlyAnalysis> analyses,
            GoalProjection projection,
            List<ProductRecommendation> recommendations
    ) {
        List<String> highlights = new ArrayList<>();
        MonthlyAnalysis previous = analyses.getFirst();
        MonthlyAnalysis current = analyses.getLast();

        if (language == SupportedLanguage.EN) {
            highlights.add("Net cashflow this month is %s, which has %s versus last month.".formatted(
                    current.netCashflow().toPlainString(),
                    current.netCashflow().compareTo(previous.netCashflow()) >= 0 ? "improved" : "declined"
            ));
            highlights.add("Savings rate this month is %s%%. Focus first on the highest-cost expense category.".formatted(
                    current.savingsRate().stripTrailingZeros().toPlainString()
            ));
            highlights.add(projection.onTrack()
                    ? "At the current monthly savings pace, the goal is likely achievable within the target period."
                    : "At the current savings pace, the goal is likely delayed. Increase monthly contributions or reduce discretionary spending.");
        } else {
            highlights.add("本月净结余为 %s，较上月%s。".formatted(
                    current.netCashflow().toPlainString(),
                    current.netCashflow().compareTo(previous.netCashflow()) >= 0 ? "改善" : "回落"
            ));
            highlights.add("本月储蓄率 %s%%，建议优先控制占比最高的支出分类。".formatted(
                    current.savingsRate().stripTrailingZeros().toPlainString()
            ));
            highlights.add(projection.onTrack()
                    ? "按当前月度储蓄能力，目标大概率可在期限内完成。"
                    : "按当前储蓄节奏，目标将延期完成，建议提高月度投入或下调可选消费。");
        }

        if (!recommendations.isEmpty()) {
            if (language == SupportedLanguage.EN) {
                highlights.add("Priority can be given to products like %s that align with the user's risk profile and time horizon."
                        .formatted(recommendations.getFirst().product().getProductName()));
            } else {
                highlights.add("优先关注 %s 等与风险等级一致、期限匹配的产品。"
                        .formatted(recommendations.getFirst().product().getProductName()));
            }
        }

        return highlights;
    }

    public List<String> buildSavingsAdvice(SupportedLanguage language, List<MonthlyAnalysis> analyses) {
        MonthlyAnalysis current = analyses.getLast();
        List<String> advice = new ArrayList<>();
        current.expenseByCategory().entrySet().stream().limit(2).forEach(entry -> {
            if (language == SupportedLanguage.EN) {
                advice.add("Reduce spending in " + entry.getKey() + " first. Current amount is about " + entry.getValue().toPlainString());
            } else {
                advice.add("建议优先压缩 " + entry.getKey() + " 支出，当前金额约 " + entry.getValue().toPlainString());
            }
        });
        advice.add(language == SupportedLanguage.EN
                ? "Automatically transfer monthly surplus into savings or investment accounts to avoid discretionary spending leakage."
                : "建议将每月净结余优先自动转入储蓄或投资账户，避免被可选消费侵蚀。");
        return advice;
    }

    public String buildStructuredRecommendationAnswer(
            com.smartwealth.ai.service.model.WealthInsight insight,
            List<LlmProductSelection> selections
    ) {
        var scenario = insight.goalScenarioAnalysis();
        var projection = insight.goalProjection();
        MonthlyAnalysis current = insight.monthlyAnalyses().getLast();
        boolean english = insight.language() == SupportedLanguage.EN;

        if (selections == null || selections.isEmpty()) {
            if (english) {
                return """
                        Current savings are %s %s, the required down payment is %s %s, and the funding gap is %s %s.
                        Based on a monthly contribution of %s %s, the target is estimated to be reached in about %d months, around %s.
                        Reduce the largest expense categories first and keep monthly savings stable.
                        """.formatted(
                        scenario.currentSavingsBalance().toPlainString(),
                        scenario.currency(),
                        scenario.requiredDownPayment().toPlainString(),
                        scenario.currency(),
                        scenario.savingsGap().toPlainString(),
                        scenario.currency(),
                        projection.projectedMonthlyContribution().toPlainString(),
                        scenario.currency(),
                        scenario.estimatedMonthsToReachGoal(),
                        scenario.estimatedReachDate()
                ).trim();
            }
            return """
                    当前存款为 %s %s，目标首付为 %s %s，资金缺口为 %s %s。
                    按当前每月可投入 %s %s 计算，预计约 %d 个月可以达到目标，预计日期为 %s。
                    建议优先从当前支出最高的项目开始压缩，并持续保留稳定月度储蓄。
                    """.formatted(
                    scenario.currentSavingsBalance().toPlainString(),
                    scenario.currency(),
                    scenario.requiredDownPayment().toPlainString(),
                    scenario.currency(),
                    scenario.savingsGap().toPlainString(),
                    scenario.currency(),
                    projection.projectedMonthlyContribution().toPlainString(),
                    scenario.currency(),
                    scenario.estimatedMonthsToReachGoal(),
                    scenario.estimatedReachDate()
            ).trim();
        }

        LlmProductSelection selected = selections.getFirst();
        ProductRecommendation selectedRecommendation = selected.product();
        List<InvestmentPlan> matchedPlans = insight.investmentPlans().stream()
                .filter(plan -> plan.productCode().equals(selectedRecommendation.product().getProductCode()))
                .toList();

        String planComparison = matchedPlans.stream()
                .map(plan -> english
                        ? """
                        - With monthly saving of %s %s, it would take about %d months. With monthly investing of the same %s %s into this product, it would take about %d months, generate about %s %s in gains, reach the goal around %s, and save about %d months versus saving only.
                        """.formatted(
                        plan.monthlyInvestmentAmount().stripTrailingZeros().toPlainString(),
                        scenario.currency(),
                        plan.savingsOnlyMonthsForSameContribution(),
                        plan.monthlyInvestmentAmount().stripTrailingZeros().toPlainString(),
                        scenario.currency(),
                        plan.investmentMonths(),
                        plan.estimatedInvestmentGain().stripTrailingZeros().toPlainString(),
                        scenario.currency(),
                        plan.estimatedReachDate(),
                        plan.savedMonthsComparedToSavingOnly()
                ).trim()
                        : """
                        - 若每月储蓄 %s %s，预计需要 %d 个月；若每月投资同样的 %s %s 于该产品，预计需要 %d 个月，预计投资收益约 %s %s，预计在 %s 达到目标，并较同金额单纯储蓄节约 %d 个月
                        """.formatted(
                        plan.monthlyInvestmentAmount().stripTrailingZeros().toPlainString(),
                        scenario.currency(),
                        plan.savingsOnlyMonthsForSameContribution(),
                        plan.monthlyInvestmentAmount().stripTrailingZeros().toPlainString(),
                        scenario.currency(),
                        plan.investmentMonths(),
                        plan.estimatedInvestmentGain().stripTrailingZeros().toPlainString(),
                        scenario.currency(),
                        plan.estimatedReachDate(),
                        plan.savedMonthsComparedToSavingOnly()
                ).trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse(english ? "- No comparable investment plan is currently available." : "- 当前暂无可比较的投资计划");

        String savingsAdvice = current.expenseByCategory().entrySet().stream()
                .limit(2)
                .map(entry -> english
                        ? entry.getKey() + " spending is about " + entry.getValue().toPlainString()
                        : entry.getKey() + " 支出约 " + entry.getValue().toPlainString())
                .reduce((left, right) -> left + "，" + right)
                .orElse(english ? "there is no obvious high-expense category yet" : "暂无明显高支出分类");

        if (english) {
            return """
                    Current savings are %s %s, the required down payment is %s %s, and the funding gap is %s %s.
                    Based on a monthly contribution ceiling of %s %s, pure saving would take about %d months, reaching the goal around %s.

                    The recommended product is %s (%s), with an annualized return of about %s, a minimum holding period of %d days, and liquidity level %s.
                    Recommendation reason: %s
                    Risk and compliance note: %s

                    Compare these investment plans:
                    %s

                    Also prioritize optimizing %s to increase stable investable cashflow.
                    """.formatted(
                    scenario.currentSavingsBalance().stripTrailingZeros().toPlainString(),
                    scenario.currency(),
                    scenario.requiredDownPayment().stripTrailingZeros().toPlainString(),
                    scenario.currency(),
                    scenario.savingsGap().stripTrailingZeros().toPlainString(),
                    scenario.currency(),
                    projection.projectedMonthlyContribution().stripTrailingZeros().toPlainString(),
                    scenario.currency(),
                    scenario.estimatedMonthsToReachGoal(),
                    scenario.estimatedReachDate(),
                    selectedRecommendation.product().getProductName(),
                    selectedRecommendation.product().getProductCode(),
                    selectedRecommendation.product().getAnnualReturnRate().movePointRight(2).stripTrailingZeros().toPlainString() + "%",
                    selectedRecommendation.product().getMinHoldingDays(),
                    selectedRecommendation.product().getLiquidityLevel().name(),
                    selected.reason(),
                    selectedRecommendation.product().getComplianceNote(),
                    planComparison,
                    savingsAdvice
            ).trim();
        }
        return """
                当前存款为 %s %s，目标首付为 %s %s，资金缺口为 %s %s。
                按当前每月可投入上限 %s %s 计算，单纯储蓄预计约 %d 个月达到目标，预计日期为 %s。

                推荐产品为 %s（%s），年化收益率约 %s，最短持有期 %d 天，流动性 %s。
                推荐理由：%s
                合规与风险提示：%s

                建议重点比较以下投资方案：
                %s

                同时建议优先优化 %s，以提高稳定可投资金额。
                """.formatted(
                scenario.currentSavingsBalance().stripTrailingZeros().toPlainString(),
                scenario.currency(),
                scenario.requiredDownPayment().stripTrailingZeros().toPlainString(),
                scenario.currency(),
                scenario.savingsGap().stripTrailingZeros().toPlainString(),
                scenario.currency(),
                projection.projectedMonthlyContribution().stripTrailingZeros().toPlainString(),
                scenario.currency(),
                scenario.estimatedMonthsToReachGoal(),
                scenario.estimatedReachDate(),
                selectedRecommendation.product().getProductName(),
                selectedRecommendation.product().getProductCode(),
                selectedRecommendation.product().getAnnualReturnRate().movePointRight(2).stripTrailingZeros().toPlainString() + "%",
                selectedRecommendation.product().getMinHoldingDays(),
                selectedRecommendation.product().getLiquidityLevel().name(),
                selected.reason(),
                selectedRecommendation.product().getComplianceNote(),
                planComparison,
                savingsAdvice
        ).trim();
    }

    public String buildFallbackAnswer(
            Long userId,
            SupportedLanguage language,
            List<MonthlyAnalysis> analyses,
            GoalProjection projection,
            com.smartwealth.ai.service.model.GoalScenarioAnalysis scenario,
            List<ProductRecommendation> recommendations,
            List<InvestmentPlan> investmentPlans,
            List<String> highlights,
            List<String> ragContextSnippets,
            String userQuestion
    ) {
        MonthlyAnalysis current = analyses.getLast();
        boolean english = language == SupportedLanguage.EN;
        String topExpenseCategory = current.expenseByCategory().entrySet().stream()
                .findFirst()
                .map(entry -> entry.getKey() + " " + entry.getValue().toPlainString())
                .orElse(english ? "no dominant expense category" : "暂无明显支出分类");

        String recommendedNames = recommendations.stream()
                .map(item -> item.product().getProductName())
                .reduce((left, right) -> english ? left + ", " + right : left + "、" + right)
                .orElse(english ? "no matching product at the moment" : "暂无适配产品");

        String productDetail = recommendations.stream()
                .findFirst()
                .map(item -> english
                        ? "%s(%s), annualized return %s, minimum holding period %d days, liquidity %s, compliance note: %s".formatted(
                        item.product().getProductName(),
                        item.product().getProductCode(),
                        item.product().getAnnualReturnRate().movePointRight(2).stripTrailingZeros().toPlainString() + "%",
                        item.product().getMinHoldingDays(),
                        item.product().getLiquidityLevel().name(),
                        item.product().getComplianceNote()
                )
                        : "%s(%s)，年化收益 %s，最短持有 %d 天，流动性 %s，合规提示：%s".formatted(
                        item.product().getProductName(),
                        item.product().getProductCode(),
                        item.product().getAnnualReturnRate().movePointRight(2).stripTrailingZeros().toPlainString() + "%",
                        item.product().getMinHoldingDays(),
                        item.product().getLiquidityLevel().name(),
                        item.product().getComplianceNote()
                ))
                .orElse(english ? "no product details available" : "暂无适配产品细节");

        String investmentPlanDetail = investmentPlans.stream()
                .map(plan -> english
                        ? "With monthly saving of %s %s, it would take about %d months; with monthly investing of the same %s %s into this product at about %s annualized return for %d months, estimated gain is about %s %s, expected value at %s is %s, and this saves about %d months versus saving only.".formatted(
                        plan.monthlyInvestmentAmount().toPlainString(),
                        scenario.currency(),
                        plan.savingsOnlyMonthsForSameContribution(),
                        plan.monthlyInvestmentAmount().toPlainString(),
                        scenario.currency(),
                        plan.annualReturnRate().movePointRight(2).stripTrailingZeros().toPlainString() + "%",
                        plan.investmentMonths(),
                        plan.estimatedInvestmentGain().toPlainString(),
                        scenario.currency(),
                        plan.estimatedReachDate(),
                        plan.estimatedTotalValueAtGoalDate().toPlainString(),
                        plan.savedMonthsComparedToSavingOnly()
                )
                        : "若每月储蓄 %s %s，预计需要 %d 个月；若每月投资同样的 %s %s 于该产品，产品年化收益率约 %s，持续 %d 个月，预计投资收益约 %s %s，预计在 %s 达到 %s，并较同金额单纯储蓄节约 %d 个月。".formatted(
                        plan.monthlyInvestmentAmount().toPlainString(),
                        scenario.currency(),
                        plan.savingsOnlyMonthsForSameContribution(),
                        plan.monthlyInvestmentAmount().toPlainString(),
                        scenario.currency(),
                        plan.annualReturnRate().movePointRight(2).stripTrailingZeros().toPlainString() + "%",
                        plan.investmentMonths(),
                        plan.estimatedInvestmentGain().toPlainString(),
                        scenario.currency(),
                        plan.estimatedReachDate(),
                        plan.estimatedTotalValueAtGoalDate().toPlainString(),
                        plan.savedMonthsComparedToSavingOnly()
                ))
                .reduce((left, right) -> english ? left + " " + right : left + "；" + right)
                .orElse(english ? "No investment plan was generated." : "当前未生成投资计划。");

        if (english) {
            return """
                    Wealth analysis for user %d:
                    1. Cashflow analysis is completed for the previous month and the current month. This month income is %s, expense is %s, net cashflow is %s, and the largest expense category is %s.
                    2. Based on private risk and goal knowledge, the current savings goal is %s. It is estimated to take %d more months and reach around %s. %s.
                    3. In the current scenario, available savings are %s %s, required down payment is %s %s, the funding gap is %s %s, and with a current monthly contribution of %s %s it would take about %d months to close the gap.
                    4. %s
                    5. Investment path: %s
                    6. Suggestions: %s

                    User question: %s
                    RAG reference snippets: %s
                    """.formatted(
                    userId,
                    current.income().toPlainString(),
                    current.expense().toPlainString(),
                    current.netCashflow().toPlainString(),
                    topExpenseCategory,
                    projection.goalName(),
                    projection.monthsToGoal(),
                    projection.projectedCompletionDate(),
                    projection.onTrack() ? "Current progress is within the target timeline" : "Current progress is later than the target timeline",
                    scenario.currentSavingsBalance().toPlainString(),
                    scenario.currency(),
                    scenario.requiredDownPayment().toPlainString(),
                    scenario.currency(),
                    scenario.savingsGap().toPlainString(),
                    scenario.currency(),
                    projection.projectedMonthlyContribution().toPlainString(),
                    scenario.currency(),
                    scenario.estimatedMonthsToReachGoal(),
                    recommendations.isEmpty()
                            ? "This question is mainly about affordability, so no product is proactively recommended. If you want a plan to reach the goal or product options, I can continue with matching products and investment paths."
                            : "Recommended products: " + recommendedNames + ". Product detail: " + productDetail,
                    investmentPlanDetail,
                    String.join("; ", highlights),
                    userQuestion,
                    String.join(" | ", ragContextSnippets)
            ).trim();
        }
        return """
                用户 %d 的财富分析如下：
                1. 上月和本月已完成收支分析，本月收入 %s，支出 %s，净结余 %s，最大支出分类为 %s。
                2. 结合私有知识库检索到的风险等级与目标信息，当前储蓄目标为 %s，预计还需 %d 个月完成，预计完成日期 %s，%s。
                3. 当前场景下，现有存款为 %s %s，目标首付为 %s %s，资金缺口为 %s %s，按当前月度可投入 %s %s 计算，预计约 %d 个月可补齐。
                4. %s
                5. 投资达成路径：%s
                6. 建议：%s

                用户问题：%s
                RAG 参考片段：%s
                """.formatted(
                userId,
                current.income().toPlainString(),
                current.expense().toPlainString(),
                current.netCashflow().toPlainString(),
                topExpenseCategory,
                projection.goalName(),
                projection.monthsToGoal(),
                projection.projectedCompletionDate(),
                projection.onTrack() ? "当前进度在目标期限内" : "当前进度晚于目标期限",
                scenario.currentSavingsBalance().toPlainString(),
                scenario.currency(),
                scenario.requiredDownPayment().toPlainString(),
                scenario.currency(),
                scenario.savingsGap().toPlainString(),
                scenario.currency(),
                projection.projectedMonthlyContribution().toPlainString(),
                scenario.currency(),
                scenario.estimatedMonthsToReachGoal(),
                recommendations.isEmpty()
                        ? "当前问题主要在判断支付能力，暂不主动推荐产品。若你想了解如何达成目标或需要产品方案，我可以继续给出匹配产品与投资路径。"
                        : "推荐产品：" + recommendedNames + "。产品细节：" + productDetail,
                investmentPlanDetail,
                String.join("；", highlights),
                userQuestion,
                String.join(" | ", ragContextSnippets)
        ).trim();
    }
}
