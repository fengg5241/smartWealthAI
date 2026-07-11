package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.WaCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WaCustomerRepository extends JpaRepository<WaCustomer, Long> {
    Optional<WaCustomer> findByTenantIdAndWaPhone(String tenantId, String waPhone);
}
