package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.AuditLog;
import com.smartwealth.ai.domain.ImTenantMapping;
import com.smartwealth.ai.domain.SyncAuthToken;
import com.smartwealth.ai.domain.Tenant;
import com.smartwealth.ai.repository.AuditLogRepository;
import com.smartwealth.ai.repository.ImTenantMappingRepository;
import com.smartwealth.ai.repository.SyncAuthTokenRepository;
import com.smartwealth.ai.repository.TenantRepository;
import com.smartwealth.ai.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;
    private final ImTenantMappingRepository imTenantMappingRepository;
    private final SyncAuthTokenRepository syncAuthTokenRepository;

    public AdminController(AuditLogRepository auditLogRepository, TenantRepository tenantRepository,
                           ImTenantMappingRepository imTenantMappingRepository,
                           SyncAuthTokenRepository syncAuthTokenRepository) {
        this.auditLogRepository = auditLogRepository;
        this.tenantRepository = tenantRepository;
        this.imTenantMappingRepository = imTenantMappingRepository;
        this.syncAuthTokenRepository = syncAuthTokenRepository;
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

    /**
     * List all tenants with their external platform bindings.
     */
    @GetMapping("/tenants")
    public ResponseEntity<?> listTenants() {
        if (!TenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        List<ImTenantMapping> imMappings = imTenantMappingRepository.findAll();
        List<SyncAuthToken> syncTokens = syncAuthTokenRepository.findAll();

        List<Map<String, Object>> tenants = tenantRepository.findAll().stream()
                .map(t -> {
                    String tid = t.getTenantId();
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("tenantId", tid);
                    info.put("name", t.getName());
                    info.put("tenantGroup", t.getTenantGroup() != null ? t.getTenantGroup() : "enterprise");

                    // Find IM bindings for this tenant
                    imMappings.stream()
                            .filter(m -> tid.equals(m.getTenantId()))
                            .forEach(m -> info.put(m.getPlatform(), m.getPlatformTeamId()));

                    // Find sync bindings for this tenant
                    syncTokens.stream()
                            .filter(s -> tid.equals(s.getTenantId()))
                            .forEach(s -> info.put(s.getPlatform(), true));

                    return (Map<String, Object>) info;
                })
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
        String tenantGroup = body.getOrDefault("tenantGroup", "enterprise");
        if (!tenantGroup.equals("enterprise") && !tenantGroup.equals("study") && !tenantGroup.equals("all")) {
            return ResponseEntity.badRequest().body(Map.of("error", "tenantGroup must be 'enterprise', 'study', or 'all'"));
        }
        if (tenantId == null || tenantId.isBlank() || name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "tenantId and name are required"));
        }
        if (tenantRepository.findByTenantId(tenantId).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tenant '" + tenantId + "' already exists"));
        }
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName(name);
        tenant.setTenantGroup(tenantGroup);
        tenantRepository.save(tenant);
        return ResponseEntity.ok(Map.of("message", "Tenant created", "tenantId", tenantId, "name", name, "tenantGroup", tenantGroup));
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

    @PutMapping("/tenants/{tenantId}")
    public ResponseEntity<?> updateTenant(@PathVariable String tenantId, @RequestBody Map<String, String> body) {
        if (!TenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        Tenant tenant = tenantRepository.findByTenantId(tenantId).orElse(null);
        if (tenant == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tenant not found: " + tenantId));
        }
        String name = body.get("name");
        String tenantGroup = body.get("tenantGroup");
        if (name != null && !name.isBlank()) tenant.setName(name);
        if (tenantGroup != null) {
            if (!tenantGroup.equals("enterprise") && !tenantGroup.equals("study") && !tenantGroup.equals("all")) {
                return ResponseEntity.badRequest().body(Map.of("error", "tenantGroup must be 'enterprise', 'study', or 'all'"));
            }
            tenant.setTenantGroup(tenantGroup);
        }
        tenantRepository.save(tenant);
        return ResponseEntity.ok(Map.of("message", "Tenant updated", "tenantId", tenantId,
                "name", tenant.getName(), "tenantGroup", tenant.getTenantGroup()));
    }

    // --- IM Bot Tenant Mappings ---

    @GetMapping("/im-mappings")
    public ResponseEntity<?> listImMappings() {
        if (!TenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        return ResponseEntity.ok(imTenantMappingRepository.findAll());
    }

    @PostMapping("/im-mappings")
    public ResponseEntity<?> createImMapping(@RequestBody Map<String, String> body) {
        if (!TenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        String platform = body.get("platform");
        String platformTeamId = body.get("platformTeamId");
        String tenantId = body.get("tenantId");
        if (platform == null || platformTeamId == null || tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "platform, platformTeamId, and tenantId are required"));
        }
        if (!platform.equals("slack") && !platform.equals("wecom") && !platform.equals("whatsapp")) {
            return ResponseEntity.badRequest().body(Map.of("error", "platform must be 'slack', 'wecom', or 'whatsapp'"));
        }

        ImTenantMapping existing = imTenantMappingRepository
                .findByPlatformAndPlatformTeamId(platform, platformTeamId).orElse(null);
        if (existing != null) {
            existing.setTenantId(tenantId);
            imTenantMappingRepository.save(existing);
            return ResponseEntity.ok(Map.of("message", "Mapping updated"));
        }

        ImTenantMapping mapping = new ImTenantMapping(platform, platformTeamId, tenantId);
        imTenantMappingRepository.save(mapping);
        return ResponseEntity.ok(Map.of("message", "Mapping created"));
    }

    @DeleteMapping("/im-mappings/{id}")
    public ResponseEntity<?> deleteImMapping(@PathVariable Long id) {
        if (!TenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        imTenantMappingRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Mapping deleted"));
    }

    // --- Sync Status ---

    @GetMapping("/sync-status")
    public ResponseEntity<?> listSyncStatus() {
        if (!TenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }
        return ResponseEntity.ok(syncAuthTokenRepository.findAll());
    }
}
