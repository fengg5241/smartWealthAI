package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.EnterpriseDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnterpriseDocumentRepository extends JpaRepository<EnterpriseDocument, Long> {
    List<EnterpriseDocument> findByTenantId(String tenantId);
    List<EnterpriseDocument> findByTenantIdAndFileName(String tenantId, String fileName);
}
