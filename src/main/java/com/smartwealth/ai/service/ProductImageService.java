package com.smartwealth.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwealth.ai.config.DemoProperties;
import com.smartwealth.ai.domain.ProductImage;
import com.smartwealth.ai.repository.ProductImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class ProductImageService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);

    private final ProductImageRepository repository;
    private final ImageEmbeddingService embeddingService;
    private final DemoProperties properties;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ProductImageService(ProductImageRepository repository,
                               ImageEmbeddingService embeddingService,
                               DemoProperties properties,
                               JdbcTemplate jdbc) {
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.properties = properties;
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public ProductImage indexProduct(String tenantId, String productName, String category,
                                     String description, byte[] imageBytes, String contentType) {
        var dir = properties.getImageSearch().getUploadDir();
        Path tenantDir = Paths.get(dir, tenantId);
        try {
            Files.createDirectories(tenantDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload directory: " + tenantDir, e);
        }

        ProductImage entity = new ProductImage();
        entity.setTenantId(tenantId);
        entity.setProductName(productName.trim());
        entity.setCategory(category != null ? category.trim() : "");
        entity.setDescription(description != null ? description.trim() : "");
        entity.setImageContentType(contentType);
        entity = repository.save(entity);

        String filename = entity.getId() + ".jpg";
        entity.setImagePath(tenantDir.resolve(filename).toString());
        try {
            Files.write(Paths.get(entity.getImagePath()), imageBytes);
        } catch (IOException e) {
            repository.delete(entity);
            throw new RuntimeException("Failed to save image file", e);
        }

        try {
            float[] embedding = embeddingService.embedImage(imageBytes, contentType);

            String vectorId = UUID.nameUUIDFromBytes(
                    (tenantId + "-product-" + entity.getId()).getBytes()).toString();
            entity.setVectorId(vectorId);

            String searchableText = "Product: " + productName
                    + " | Category: " + (category != null ? category : "")
                    + " | Description: " + (description != null ? description : "");

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("tenantId", tenantId);
            metadata.put("productId", entity.getId().toString());
            metadata.put("productName", productName);
            metadata.put("category", category != null ? category : "");
            metadata.put("description", description != null ? description : "");

            jdbc.update(
                    "INSERT INTO vector_store_image (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?::vector)",
                    vectorId,
                    searchableText,
                    objectMapper.writeValueAsString(metadata),
                    vectorToString(embedding));

            entity = repository.save(entity);
            log.info("Indexed product '{}' (id={}) for tenant={}", productName, entity.getId(), tenantId);
            return entity;
        } catch (Exception e) {
            // Clean up the file on failure
            try {
                Files.deleteIfExists(Paths.get(entity.getImagePath()));
            } catch (IOException ignored) { }
            repository.delete(entity);
            throw new RuntimeException("Failed to index product: " + e.getMessage(), e);
        }
    }

    public List<ProductSearchResult> search(String tenantId, byte[] queryImageBytes, int topK) {
        float[] embedding = embeddingService.embedImage(queryImageBytes, null);
        String vectorStr = vectorToString(embedding);

        String sql = """
                SELECT content, metadata, 1 - (embedding <=> ?::vector) AS similarity
                FROM vector_store_image
                WHERE (metadata ->> 'tenantId') = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;

        var results = jdbc.query(sql,
                ps -> {
                    ps.setString(1, vectorStr);
                    ps.setString(2, tenantId);
                    ps.setString(3, vectorStr);
                    ps.setInt(4, topK);
                },
                (rs, rowNum) -> {
                    double similarity = rs.getDouble("similarity");
                    String metaJson = rs.getString("metadata");
                    try {
                        JsonNode meta = objectMapper.readTree(metaJson);
                        return new ProductSearchResult(
                                Long.parseLong(meta.path("productId").asText()),
                                meta.path("productName").asText(),
                                meta.path("category").asText(""),
                                meta.path("description").asText(""),
                                similarity);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse metadata JSON: {}", metaJson);
                        return null;
                    }
                });

        return results.stream().filter(Objects::nonNull).toList();
    }

    public List<ProductImage> listProducts(String tenantId, String category) {
        if (category != null && !category.isBlank()) {
            return repository.findByTenantIdAndCategoryOrderByUploadTimeDesc(tenantId, category);
        }
        return repository.findByTenantIdOrderByUploadTimeDesc(tenantId);
    }

    public Optional<ProductImage> getProduct(String tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId);
    }

    @Transactional
    public ProductImage updateProduct(String tenantId, Long id, String productName, String category, String description) {
        ProductImage entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));

        if (productName != null && !productName.isBlank()) {
            entity.setProductName(productName.trim());
        }
        if (category != null) {
            entity.setCategory(category.trim());
        }
        if (description != null) {
            entity.setDescription(description.trim());
        }

        // Update the searchable text in vector store
        if (entity.getVectorId() != null) {
            String searchableText = "Product: " + entity.getProductName()
                    + " | Category: " + entity.getCategory()
                    + " | Description: " + entity.getDescription();
            jdbc.update("UPDATE vector_store_image SET content = ? WHERE id = ?",
                    searchableText, entity.getVectorId());
        }

        return repository.save(entity);
    }

    @Transactional
    public void deleteProduct(String tenantId, Long productId) {
        ProductImage entity = repository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));

        if (entity.getVectorId() != null) {
            jdbc.update("DELETE FROM vector_store_image WHERE id = ?", entity.getVectorId());
        }

        if (entity.getImagePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(entity.getImagePath()));
            } catch (IOException e) {
                log.warn("Failed to delete image file: {}", entity.getImagePath());
            }
        }

        repository.delete(entity);
        log.info("Deleted product id={} for tenant={}", productId, tenantId);
    }

    public List<String> getCategories(String tenantId) {
        return repository.findByTenantIdOrderByUploadTimeDesc(tenantId).stream()
                .map(ProductImage::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static String vectorToString(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public record ProductSearchResult(Long productId, String productName, String category,
                                       String description, double similarity) {}
}
