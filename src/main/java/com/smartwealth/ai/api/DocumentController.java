package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.EnterpriseDocument;
import com.smartwealth.ai.repository.EnterpriseDocumentRepository;
import com.smartwealth.ai.service.DocumentParserService;
import com.smartwealth.ai.service.RagDocumentService;
import com.smartwealth.ai.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final long MAX_TEXT_LENGTH = 500_000;

    private final DocumentParserService parserService;
    private final RagDocumentService ragDocumentService;
    private final EnterpriseDocumentRepository documentRepository;

    public DocumentController(DocumentParserService parserService, RagDocumentService ragDocumentService,
                              EnterpriseDocumentRepository documentRepository) {
        this.parserService = parserService;
        this.ragDocumentService = ragDocumentService;
        this.documentRepository = documentRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        try {
            String fileName = file.getOriginalFilename();
            String fileType = detectFileType(fileName);
            if ("UNKNOWN".equals(fileType)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Unsupported file type. Supported: PDF, DOCX, XLSX, XLS"));
            }
            String content = parserService.parse(file);
            if (content.length() > MAX_TEXT_LENGTH) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Document text content exceeds " + MAX_TEXT_LENGTH + " characters. Please upload a smaller file or split it into multiple documents."));
            }
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

    @GetMapping
    public ResponseEntity<?> list() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }
        List<Map<String, Object>> docs = documentRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.groupingBy(EnterpriseDocument::getFileName))
                .entrySet().stream()
                .map(e -> Map.of(
                        "fileName", (Object) e.getKey(),
                        "fileType", e.getValue().get(0).getFileType(),
                        "chunks", (Object) e.getValue().size(),
                        "uploadTime", e.getValue().get(0).getUploadTime()))
                .toList();
        return ResponseEntity.ok(docs);
    }

    @DeleteMapping("/{fileName:.+}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String fileName) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }
        ragDocumentService.deleteDocument(tenantId, fileName);
        return ResponseEntity.ok(Map.of(
                "message", "Document deleted",
                "fileName", fileName));
    }

    private String detectFileType(String fileName) {
        if (fileName == null) return "UNKNOWN";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf"))  return "PDF";
        if (lower.endsWith(".docx")) return "DOCX";
        if (lower.endsWith(".xlsx")) return "XLSX";
        if (lower.endsWith(".xls"))  return "XLS";
        return "UNKNOWN";
    }
}
