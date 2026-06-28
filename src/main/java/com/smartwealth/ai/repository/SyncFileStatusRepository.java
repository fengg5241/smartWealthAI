package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.SyncFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncFileStatusRepository extends JpaRepository<SyncFileStatus, Long> {

    Optional<SyncFileStatus> findByPlatformAndTenantIdAndFileId(String platform, String tenantId, String fileId);

    List<SyncFileStatus> findByPlatformAndTenantId(String platform, String tenantId);

    void deleteByPlatformAndTenantIdAndFileId(String platform, String tenantId, String fileId);
}
