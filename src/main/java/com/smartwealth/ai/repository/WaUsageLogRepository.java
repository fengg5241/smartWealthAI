package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.WaUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface WaUsageLogRepository extends JpaRepository<WaUsageLog, Long> {

    int countByTenantIdAndCreatedAtAfter(String tenantId, LocalDateTime after);

    @Query("SELECT COUNT(w) FROM WaUsageLog w WHERE w.tenantId = :tenantId AND w.direction = 'outbound' AND w.createdAt > :since")
    int countOutboundSince(@Param("tenantId") String tenantId, @Param("since") LocalDateTime since);
}
