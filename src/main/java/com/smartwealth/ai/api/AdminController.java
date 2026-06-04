package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.AuditLog;
import com.smartwealth.ai.repository.AuditLogRepository;
import com.smartwealth.ai.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuditLogRepository auditLogRepository;

    public AdminController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }
        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByTimestampDesc(tenantId);
        return ResponseEntity.ok(logs);
    }
}
