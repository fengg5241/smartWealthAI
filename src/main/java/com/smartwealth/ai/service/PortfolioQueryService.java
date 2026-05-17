package com.smartwealth.ai.service;

import com.smartwealth.ai.domain.ProductCategory;
import com.smartwealth.ai.repository.UserPortfolioHoldingRepository;
import com.smartwealth.ai.service.model.PortfolioAllocationSummary;
import com.smartwealth.ai.service.model.PortfolioHoldingSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PortfolioQueryService {

    private final UserPortfolioHoldingRepository userPortfolioHoldingRepository;

    public PortfolioQueryService(UserPortfolioHoldingRepository userPortfolioHoldingRepository) {
        this.userPortfolioHoldingRepository = userPortfolioHoldingRepository;
    }

    public List<PortfolioHoldingSnapshot> holdings(Long userId) {
        return userPortfolioHoldingRepository.findByUserIdOrderByAllocationPercentDesc(userId).stream()
                .map(item -> new PortfolioHoldingSnapshot(
                        item.getProduct().getProductCode(),
                        item.getPositionName(),
                        item.getProduct().getProductCategory(),
                        item.getCurrency(),
                        item.getInvestedAmount(),
                        item.getCurrentValue(),
                        item.getAllocationPercent(),
                        item.getOpenedAt()
                ))
                .toList();
    }

    public List<PortfolioAllocationSummary> allocationByCategory(Long userId) {
        List<PortfolioHoldingSnapshot> holdings = holdings(userId);
        Map<ProductCategory, BigDecimal> totals = holdings.stream()
                .collect(Collectors.groupingBy(
                        PortfolioHoldingSnapshot::productCategory,
                        Collectors.mapping(PortfolioHoldingSnapshot::currentValue, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));
        BigDecimal totalValue = holdings.stream()
                .map(PortfolioHoldingSnapshot::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totals.entrySet().stream()
                .map(entry -> new PortfolioAllocationSummary(
                        entry.getKey(),
                        totalValue.signum() == 0
                                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                                : entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP),
                        entry.getValue().setScale(2, RoundingMode.HALF_UP)
                ))
                .sorted(Comparator.comparing(PortfolioAllocationSummary::allocationPercent).reversed())
                .toList();
    }
}
