package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.SyncAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncAuthTokenRepository extends JpaRepository<SyncAuthToken, Long> {

    Optional<SyncAuthToken> findByPlatformAndTenantId(String platform, String tenantId);
}
