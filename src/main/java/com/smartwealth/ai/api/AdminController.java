package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.AuditLog;
import com.smartwealth.ai.domain.Tenant;
import com.smartwealth.ai.repository.AuditLogRepository;
import com.smartwealth.ai.repository.TenantRepository;
import com.smartwealth.ai.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;

    public AdminController(AuditLogRepository auditLogRepository, TenantRepository tenantRepository) {
        this.auditLogRepository = auditLogRepository;
        this.tenantRepository = tenantRepository;
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

    @GetMapping("/tenants")
    public ResponseEntity<?> listTenants() {
        if (!TenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        List<Map<String, Object>> tenants = tenantRepository.findAll().stream()
                .map(t -> Map.of(
                        "tenantId", (Object) t.getTenantId(),
                        "name", (Object) t.getName()))
                .toList();
        return ResponseEntity.ok(tenants);
    }

    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@RequestBody Map<String, String> body) {
        if (!TenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        String tenantId = body.get("tenantId");
        String name = body.get("name");
        if (tenantId == null || tenantId.isBlank() || name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "tenantId and name are required"));
        }
        if (tenantRepository.findByTenantId(tenantId).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tenant '" + tenantId + "' already exists"));
        }
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName(name);
        tenantRepository.save(tenant);
        return ResponseEntity.ok(Map.of("message", "Tenant created", "tenantId", tenantId, "name", name));
    }

    @DeleteMapping("/tenants/{tenantId}")
    public ResponseEntity<?> deleteTenant(@PathVariable String tenantId) {
        if (!TenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        Tenant tenant = tenantRepository.findByTenantId(tenantId).orElse(null);
        if (tenant == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tenant not found: " + tenantId));
        }
        tenantRepository.delete(tenant);
        return ResponseEntity.ok(Map.of("message", "Tenant deleted", "tenantId", tenantId));
    }
}
