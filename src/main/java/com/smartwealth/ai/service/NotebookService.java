package com.smartwealth.ai.service;

import com.smartwealth.ai.domain.Notebook;
import com.smartwealth.ai.repository.NotebookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class NotebookService {

    private static final Logger log = LoggerFactory.getLogger(NotebookService.class);
    private final NotebookRepository repository;

    public NotebookService(NotebookRepository repository) {
        this.repository = repository;
    }

    public List<Notebook> list(String tenantId, String notebookType) {
        if (notebookType != null && !notebookType.isBlank()) {
            return repository.findByTenantIdAndNotebookTypeOrderByCreatedTimeDesc(tenantId, notebookType);
        }
        return repository.findByTenantIdOrderByCreatedTimeDesc(tenantId);
    }

    public Optional<Notebook> get(String tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId);
    }

    @Transactional
    public Notebook create(String tenantId, String name, String description, String notebookType) {
        Notebook nb = new Notebook();
        nb.setTenantId(tenantId);
        nb.setName(name.trim());
        nb.setDescription(description != null ? description.trim() : "");
        nb.setNotebookType(notebookType != null && !notebookType.isBlank() ? notebookType : "mistake");
        return repository.save(nb);
    }

    @Transactional
    public Notebook update(String tenantId, Long id, String name, String description) {
        Notebook nb = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Notebook not found: " + id));
        if (name != null && !name.isBlank()) nb.setName(name.trim());
        if (description != null) nb.setDescription(description.trim());
        return repository.save(nb);
    }

    @Transactional
    public void delete(String tenantId, Long id) {
        Notebook nb = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Notebook not found: " + id));
        repository.delete(nb);
        log.info("Deleted notebook '{}' (id={}) for tenant={}", nb.getName(), id, tenantId);
    }
}
