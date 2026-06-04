package com.smartwealth.ai.api;

import com.smartwealth.ai.service.DocumentParserService;
import com.smartwealth.ai.service.RagDocumentService;
import com.smartwealth.ai.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentParserService parserService;
    private final RagDocumentService ragDocumentService;

    public DocumentController(DocumentParserService parserService, RagDocumentService ragDocumentService) {
        this.parserService = parserService;
        this.ragDocumentService = ragDocumentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        try {
            String fileName = file.getOriginalFilename();
            String fileType = fileName != null && fileName.toLowerCase().endsWith(".pdf") ? "PDF" : "DOCX";
            String content = parserService.parse(file);
            int chunkCount = ragDocumentService.indexDocument(tenantId, fileName, fileType, content);

            return ResponseEntity.ok(Map.of(
                    "message", "Document uploaded and indexed",
                    "fileName", fileName,
                    "chunks", chunkCount
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
