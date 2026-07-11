package com.smartwealth.ai.service;

import com.smartwealth.ai.repository.WaUsageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    // TPS: tenantId → last request timestamp millis
    private final ConcurrentHashMap<String, Long> lastRequestTime = new ConcurrentHashMap<>();

    private final WaUsageLogRepository usageLogRepo;

    public RateLimitService(WaUsageLogRepository usageLogRepo) {
        this.usageLogRepo = usageLogRepo;
    }

    /**
     * Check if a tenant is within their rate limits. Called BEFORE any processing.
     * @return null if OK, or an error message if blocked
     */
    public String checkRateLimit(String tenantId, int perSecondLimit, int dailyLimit, int monthlyLimit) {
        // 1. TPS check (first — before DB/API costs are incurred)
        if (perSecondLimit > 0) {
            long now = System.currentTimeMillis();
            Long last = lastRequestTime.get(tenantId);
            if (last != null && (now - last) < 1000 / perSecondLimit) {
                return "Rate limit exceeded (TPS). Please slow down.";
            }
            lastRequestTime.put(tenantId, now);
        }

        // 2. Daily check
        if (dailyLimit > 0) {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            int todayCount = usageLogRepo.countByTenantIdAndCreatedAtAfter(tenantId, startOfDay);
            if (todayCount >= dailyLimit) {
                return "Daily message quota reached. Please try again tomorrow.";
            }
        }

        // 3. Monthly check
        if (monthlyLimit > 0) {
            LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            int monthCount = usageLogRepo.countByTenantIdAndCreatedAtAfter(tenantId, startOfMonth);
            if (monthCount >= monthlyLimit) {
                return "Monthly message quota reached. Please upgrade your plan.";
            }
        }

        return null; // OK
    }
}
