package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.Tenant;
import com.smartwealth.ai.repository.TenantRepository;
import com.smartwealth.ai.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TenantController {

    private final TenantRepository tenantRepository;

    public TenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/tenant/me")
    public ResponseEntity<?> getCurrentTenant() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }
        Tenant tenant = tenantRepository.findByTenantId(tenantId).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Tenant not found: " + tenantId));
        }

        boolean hasActiveSub = tenant.getSubscriptionExpiry() != null
                && !tenant.getSubscriptionExpiry().isBefore(LocalDate.now());
        boolean hasTrial = tenant.getTrialEndsAt() != null
                && !tenant.getTrialEndsAt().isBefore(LocalDate.now());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("tenantId", tenant.getTenantId());
        info.put("name", tenant.getName());
        info.put("tenantGroup", tenant.getTenantGroup() != null ? tenant.getTenantGroup() : "enterprise");
        info.put("email", tenant.getEmail());
        info.put("subscriptionExpiry", tenant.getSubscriptionExpiry() != null ? tenant.getSubscriptionExpiry().toString() : null);
        info.put("trialEndsAt", tenant.getTrialEndsAt() != null ? tenant.getTrialEndsAt().toString() : null);
        info.put("subscriptionActive", hasActiveSub);
        info.put("inTrial", hasTrial);
        info.put("cancelAtPeriodEnd", tenant.getCancelAtPeriodEnd() != null && tenant.getCancelAtPeriodEnd());

        return ResponseEntity.ok(info);
    }
}
