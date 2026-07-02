package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.Notebook;
import com.smartwealth.ai.service.NotebookService;
import com.smartwealth.ai.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/notebooks")
public class NotebookController {

    private static final Logger log = LoggerFactory.getLogger(NotebookController.class);
    private final NotebookService notebookService;

    public NotebookController(NotebookService notebookService) {
        this.notebookService = notebookService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "type", required = false) String notebookType) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        List<Notebook> notebooks = notebookService.list(tenantId,
                notebookType != null && !notebookType.isBlank() ? notebookType : null);
        return ResponseEntity.ok(Map.of("notebooks", notebooks.stream().map(this::toMap).toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        return notebookService.get(tenantId, id)
                .map(n -> ResponseEntity.ok(toMap(n)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        String name = body.get("name") instanceof String s ? s : null;
        if (name == null || name.isBlank()) return bad("name is required");

        String description = body.get("description") instanceof String s ? s : "";
        String notebookType = body.get("notebookType") instanceof String s ? s : "mistake";

        Notebook nb = notebookService.create(tenantId, name, description,
                notebookType.isBlank() ? "mistake" : notebookType);
        return ResponseEntity.ok(toMap(nb));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            String name = body.get("name") instanceof String s ? s : null;
            String description = body.get("description") instanceof String s ? s : null;
            Notebook nb = notebookService.update(tenantId, id, name, description);
            return ResponseEntity.ok(toMap(nb));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            notebookService.delete(tenantId, id);
            return ResponseEntity.ok(Map.of("message", "Deleted"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Map<String, Object> toMap(Notebook nb) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", nb.getId());
        map.put("name", nb.getName());
        map.put("description", nb.getDescription());
        map.put("notebookType", nb.getNotebookType());
        map.put("createdTime", nb.getCreatedTime() != null ? nb.getCreatedTime().toString() : "");
        return map;
    }

    private static ResponseEntity<Map<String, Object>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }
}
