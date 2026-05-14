package com.smartwealth.ai.service;

import com.smartwealth.ai.service.model.GoalProjection;
import com.smartwealth.ai.service.model.MonthlyAnalysis;
import com.smartwealth.ai.service.model.ProductRecommendation;
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

    public String buildFallbackAnswer(
            Long userId,
            List<MonthlyAnalysis> analyses,
            GoalProjection projection,
            List<ProductRecommendation> recommendations,
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

        return """
                用户 %d 的财富分析如下：
                1. 上月和本月已完成收支分析，本月收入 %s，支出 %s，净结余 %s，最大支出分类为 %s。
                2. 结合私有知识库检索到的风险等级与目标信息，当前储蓄目标为 %s，预计还需 %d 个月完成，预计完成日期 %s，%s。
                3. 推荐产品：%s。推荐逻辑是严格匹配风险等级，并结合目标期限和流动性要求筛选。
                4. 建议：%s

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
                recommendedNames,
                String.join("；", highlights),
                userQuestion,
                String.join(" | ", ragContextSnippets)
        ).trim();
    }
}
