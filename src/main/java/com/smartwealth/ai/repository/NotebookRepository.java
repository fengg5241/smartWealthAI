package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.Notebook;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotebookRepository extends JpaRepository<Notebook, Long> {
    List<Notebook> findByTenantIdOrderByCreatedTimeDesc(String tenantId);
    List<Notebook> findByTenantIdAndNotebookTypeOrderByCreatedTimeDesc(String tenantId, String notebookType);
    Optional<Notebook> findByIdAndTenantId(Long id, String tenantId);
}
