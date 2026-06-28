package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.ImTenantMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImTenantMappingRepository extends JpaRepository<ImTenantMapping, Long> {

    Optional<ImTenantMapping> findByPlatformAndPlatformTeamId(String platform, String platformTeamId);
}
