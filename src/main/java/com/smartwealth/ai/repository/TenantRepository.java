package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByTenantId(String tenantId);
    Optional<Tenant> findByEmail(String email);
    Optional<Tenant> findByStripeSubscriptionId(String stripeSubscriptionId);
    Optional<Tenant> findByStripeCustomerId(String stripeCustomerId);
}
