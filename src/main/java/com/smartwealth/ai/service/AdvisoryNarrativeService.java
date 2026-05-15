package com.smartwealth.ai.service;

import com.smartwealth.ai.service.model.LlmProductSelection;
import com.smartwealth.ai.service.model.GoalProjection;
import com.smartwealth.ai.service.model.MonthlyAnalysis;
import com.smartwealth.ai.service.model.ProductRecommendation;
import com.smartwealth.ai.service.model.InvestmentPlan;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdvisoryNarrativeService {

    public List<String> buildHighlights(
            List<MonthlyAnalysis> analyses,
            GoalProjection projection,
            List<ProductRecommendation> recommendations
    ) {
        List<String> highlights = new ArrayList<>();
        MonthlyAnalysis previous = analyses.getFirst();
        MonthlyAnalysis current = analyses.getLast();

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

        if (!recommendations.isEmpty()) {
            highlights.add("优先关注 %s 等与风险等级一致、期限匹配的产品。"
                    .formatted(recommendations.getFirst().product().getProductName()));
        }

        return highlights;
    }

    public List<String> buildSavingsAdvice(List<MonthlyAnalysis> analyses) {
        MonthlyAnalysis current = analyses.getLast();
        List<String> advice = new ArrayList<>();
        current.expenseByCategory().entrySet().stream().limit(2).forEach(entry ->
                advice.add("建议优先压缩 " + entry.getKey() + " 支出，当前金额约 " + entry.getValue().toPlainString()));
        advice.add("建议将每月净结余优先自动转入储蓄或投资账户，避免被可选消费侵蚀。");
        return advice;
    }

    public String buildStructuredRecommendationAnswer(
            com.smartwealth.ai.service.model.WealthInsight insight,
            List<LlmProductSelection> selections
    ) {
        var scenario = insight.goalScenarioAnalysis();
        var projection = insight.goalProjection();
        MonthlyAnalysis current = insight.monthlyAnalyses().getLast();

        if (selections == null || selections.isEmpty()) {
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
                .map(plan -> """
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
                .orElse("- 当前暂无可比较的投资计划");

        String savingsAdvice = current.expenseByCategory().entrySet().stream()
                .limit(2)
                .map(entry -> entry.getKey() + " 支出约 " + entry.getValue().toPlainString())
                .reduce((left, right) -> left + "，" + right)
                .orElse("暂无明显高支出分类");

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
        String topExpenseCategory = current.expenseByCategory().entrySet().stream()
                .findFirst()
                .map(entry -> entry.getKey() + " " + entry.getValue().toPlainString())
                .orElse("暂无明显支出分类");

        String recommendedNames = recommendations.stream()
                .map(item -> item.product().getProductName())
                .reduce((left, right) -> left + "、" + right)
                .orElse("暂无适配产品");

        String productDetail = recommendations.stream()
                .findFirst()
                .map(item -> "%s(%s)，年化收益 %s，最短持有 %d 天，流动性 %s，合规提示：%s".formatted(
                        item.product().getProductName(),
                        item.product().getProductCode(),
                        item.product().getAnnualReturnRate().movePointRight(2).stripTrailingZeros().toPlainString() + "%",
                        item.product().getMinHoldingDays(),
                        item.product().getLiquidityLevel().name(),
                        item.product().getComplianceNote()
                ))
                .orElse("暂无适配产品细节");

        String investmentPlanDetail = investmentPlans.stream()
                .map(plan -> "若每月储蓄 %s %s，预计需要 %d 个月；若每月投资同样的 %s %s 于该产品，产品年化收益率约 %s，持续 %d 个月，预计投资收益约 %s %s，预计在 %s 达到 %s，并较同金额单纯储蓄节约 %d 个月。".formatted(
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
                .reduce((left, right) -> left + "；" + right)
                .orElse("当前未生成投资计划。");

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
