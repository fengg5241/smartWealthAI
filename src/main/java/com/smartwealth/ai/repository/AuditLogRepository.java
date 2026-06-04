package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTenantIdOrderByTimestampDesc(String tenantId);
}
