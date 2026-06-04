package com.smartwealth.ai.service;

import com.smartwealth.ai.config.DemoProperties;
import com.smartwealth.ai.domain.EnterpriseDocument;
import com.smartwealth.ai.repository.EnterpriseDocumentRepository;
import com.smartwealth.ai.service.TextChunkingStrategy.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RagDocumentService {

    private static final Logger log = LoggerFactory.getLogger(RagDocumentService.class);

    private final VectorStore vectorStore;
    private final TextChunkingStrategy chunkingStrategy;
    private final EnterpriseDocumentRepository documentRepository;
    private final DemoProperties properties;

    public RagDocumentService(VectorStore vectorStore, TextChunkingStrategy chunkingStrategy,
                               EnterpriseDocumentRepository documentRepository, DemoProperties properties) {
        this.vectorStore = vectorStore;
        this.chunkingStrategy = chunkingStrategy;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    @Transactional
    public int indexDocument(String tenantId, String fileName, String fileType, String content) {
        List<TextSegment> segments = chunkingStrategy.chunk(content, fileName);
        if (segments.isEmpty()) {
            log.warn("No text segments produced for file: {}", fileName);
            return 0;
        }

        List<EnterpriseDocument> entities = new ArrayList<>();
        List<Document> vectorDocs = new ArrayList<>();

        for (TextSegment segment : segments) {
            EnterpriseDocument entity = new EnterpriseDocument();
            entity.setTenantId(tenantId);
            entity.setFileName(fileName);
            entity.setFileType(fileType);
            entity.setChunkText(segment.text());
            entity.setChunkIndex(segment.index());
            entity.setUploadTime(LocalDateTime.now());
            entities.add(entity);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tenantId", tenantId);
            metadata.put("fileName", fileName);
            metadata.put("chunkIndex", segment.index());

            String docId = stableUuid(tenantId + "-" + fileName + "-" + segment.index());
            vectorDocs.add(new Document(docId, segment.text(), metadata));
        }

        documentRepository.saveAll(entities);
        vectorStore.add(vectorDocs);

        log.info("Indexed {} chunks for tenant={}, file={}", segments.size(), tenantId, fileName);
        return segments.size();
    }

    public List<Document> searchSimilarChunks(String tenantId, String query) {
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(properties.getRag().getTopK())
                .similarityThreshold(properties.getRag().getSimilarityThreshold())
                .build());

        return results.stream()
                .filter(doc -> tenantId.equals(doc.getMetadata().get("tenantId")))
                .toList();
    }

    @Transactional
    public void deleteDocument(String tenantId, String fileName) {
        List<EnterpriseDocument> docs = documentRepository.findByTenantIdAndFileName(tenantId, fileName);
        List<String> idsToDelete = docs.stream()
                .map(doc -> stableUuid(tenantId + "-" + fileName + "-" + doc.getChunkIndex()))
                .toList();
        if (!idsToDelete.isEmpty()) {
            vectorStore.delete(idsToDelete);
        }
        documentRepository.deleteAll(docs);
        log.info("Deleted document: tenant={}, file={}, chunks={}", tenantId, fileName, idsToDelete.size());
    }

    private static String stableUuid(String rawId) {
        return UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
