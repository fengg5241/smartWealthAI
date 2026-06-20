# Image Search — 方案 B 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 2 天内实现"拍照搜产品"功能：产品图片入库 → 多模态向量检索 → 聊天框拍照搜索。前端采用方案 B（独立产品库页面 + 导航切换）。

**Architecture:** 
- 百炼 `multimodal-embedding-v1`（免费，1024 维）提供多模态向量化
- 独立 `vector_store_image` pgvector 表，与现有文档表完全隔离
- 独立 `ImageVectorStore` bean + `ImageEmbeddingService` + `ProductImageService` + `ProductImageController`
- 前端独立产品库页面（方案 B demo），聊天框加 📷 按钮

**Tech Stack:** Spring Boot 3.5.0, Spring AI 1.1.6, pgvector, DashScope multimodal embedding API, vanilla HTML/JS/CSS

## Global Constraints

- 不修改现有文档检索逻辑（ChatController, RagDocumentService, 文档 VectorStore）
- 所有数据通过 TenantContext 隔离
- 图片文件存 `./uploads/products/{tenantId}/`（可通过 `IMAGE_UPLOAD_DIR` 环境变量覆盖，后续切 OSS 只改配置）
- 上传时用 Qwen-VL 自动生成产品描述，前端可覆盖/手动修改
- PUT `/api/products/{id}` 支持修改产品名称/品类/描述
- 多模态 embedding 不走 OpenAI 兼容接口，用 RestTemplate 直调百炼原生 API
- `multimodal-embedding-v1` 免费 + 1024 维
- Demo 租户 ID：`test`

---

## 工时估算

| 任务 | 预计 |
|---|---|
| Task 1: DB + Domain + Config | 1.5h |
| Task 2: ImageEmbeddingService | 2h |
| Task 3: ProductImageService | 1.5h |
| Task 4: ProductImageController | 1.5h |
| Task 5: 前端产品库页面 | 4h |
| Task 6: 聊天框拍照集成 | 2h |
| Task 7: 端到端联调 | 2h |
| **合计** | **~14.5h (2 天)** |

---

### Task 1: 数据库表 + Domain Entity + VectorStore Bean

**Files:**
- Create: `src/main/java/com/smartwealth/ai/domain/ProductImage.java`
- Create: `src/main/java/com/smartwealth/ai/repository/ProductImageRepository.java`
- Modify: `src/main/resources/schema.sql`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-bailian.yml`
- Create: `src/main/java/com/smartwealth/ai/config/ImageSearchConfig.java`

**Interfaces:**
- Produces: `ProductImageRepository` (JPA repo, consumed by Task 3)
- Produces: `@Qualifier("imageVectorStore") VectorStore` bean (consumed by Task 3)
- Produces: `ProductImage` entity (consumed by Tasks 3, 4)

- [ ] **Step 1: 创建 ProductImage entity**

```java
// src/main/java/com/smartwealth/ai/domain/ProductImage.java
package com.smartwealth.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_image")
public class ProductImage extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "image_content_type", length = 50)
    private String imageContentType;

    @Column(name = "vector_id", length = 100)
    private String vectorId;

    @Column(name = "upload_time")
    private LocalDateTime uploadTime;

    @PrePersist
    void onCreate() {
        if (this.uploadTime == null) {
            this.uploadTime = LocalDateTime.now();
        }
    }

    // Getters and setters...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getImageContentType() { return imageContentType; }
    public void setImageContentType(String imageContentType) { this.imageContentType = imageContentType; }
    public String getVectorId() { return vectorId; }
    public void setVectorId(String vectorId) { this.vectorId = vectorId; }
    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }
}
```

- [ ] **Step 2: 创建 ProductImageRepository**

```java
// src/main/java/com/smartwealth/ai/repository/ProductImageRepository.java
package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByTenantIdOrderByUploadTimeDesc(String tenantId);
    List<ProductImage> findByTenantIdAndCategoryOrderByUploadTimeDesc(String tenantId, String category);
    Optional<ProductImage> findByIdAndTenantId(Long id, String tenantId);
}
```

- [ ] **Step 3: 更新 schema.sql**

```sql
-- 追加到现有 schema.sql 末尾
CREATE TABLE IF NOT EXISTS product_image (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    category VARCHAR(100),
    description TEXT,
    image_path VARCHAR(500),
    image_content_type VARCHAR(50),
    vector_id VARCHAR(100),
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pi_tenant_id ON product_image(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pi_category ON product_image(tenant_id, category);
```

- [ ] **Step 4: 更新 application.yml**

```yaml
# 追加到现有 demo 配置块下
demo:
  admin-key: ${DEMO_ADMIN_KEY:}
  rag:
    # ...existing...
  image-search:
    upload-dir: ${IMAGE_UPLOAD_DIR:uploads/products}
    top-k: 5
    similarity-threshold: 0.0
  chat:
    # ...existing...
```

- [ ] **Step 5: 更新 application-bailian.yml**

```yaml
# 追加到现有配置
spring:
  ai:
    openai:
      # ...existing...
      embedding:
        options:
          model: text-embedding-v3
    vectorstore:
      pgvector:
        dimensions: 1024
        table-name: vector_store_bailian

# 新增图片向量存储配置
demo:
  image-search:
    model: multimodal-embedding-v1
    dimensions: 1024
```

- [ ] **Step 6: 创建 ImageSearchConfig**

```java
// src/main/java/com/smartwealth/ai/config/ImageSearchConfig.java
package com.smartwealth.ai.config;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ImageSearchConfig {

    @Bean
    @ConfigurationProperties(prefix = "demo.image-search")
    public ImageSearchProperties imageSearchProperties() {
        return new ImageSearchProperties();
    }

    @Bean("imageVectorStore")
    VectorStore imageVectorStore(JdbcTemplate jdbcTemplate, ImageSearchProperties props) {
        return PgVectorStore.builder(jdbcTemplate)
                .tableName("vector_store_image")
                .dimensions(props.getDimensions())
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }

    public static class ImageSearchProperties {
        private String model = "multimodal-embedding-v1";
        private int dimensions = 1024;
        private String uploadDir = "uploads/products";
        private int topK = 5;
        private double similarityThreshold = 0.0;

        // Getters and setters...
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
        public String getUploadDir() { return uploadDir; }
        public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    }
}
```

---

### Task 2: ImageEmbeddingService — 百炼多模态 API 调用

**Files:**
- Create: `src/main/java/com/smartwealth/ai/service/ImageEmbeddingService.java`

**Interfaces:**
- Consumes: `ImageSearchProperties` (from Task 1)
- Produces: `float[] embed(byte[] imageBytes)` — consumed by Task 3
- Produces: `float[] embed(String text)` — consumed by Task 3 (optional, for text-based product search)

- [ ] **Step 1: 创建 ImageEmbeddingService**

```java
// src/main/java/com/smartwealth/ai/service/ImageEmbeddingService.java
package com.smartwealth.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwealth.ai.config.ImageSearchConfig.ImageSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ImageEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ImageEmbeddingService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ImageEmbeddingService(ImageSearchProperties props) {
        this.restTemplate = new RestTemplate();
        this.apiKey = System.getenv("DASHSCOPE_API_KEY");
        this.model = props.getModel();
        this.baseUrl = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";
        this.objectMapper = new ObjectMapper();
    }

    public float[] embed(byte[] imageBytes) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        return callApi(Map.of("image", base64Image));
    }

    public float[] embed(String text) {
        return callApi(Map.of("text", text));
    }

    private float[] callApi(Map<String, String> content) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", Map.of("contents", List.of(content)),
                    "parameters", Map.of()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Multimodal embedding API error: {}", response.getBody());
                throw new RuntimeException("Multimodal embedding API returned " + response.getStatusCode());
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode embeddings = root.path("output").path("embeddings");
            if (!embeddings.isArray() || embeddings.isEmpty()) {
                throw new RuntimeException("No embeddings in response: " + response.getBody());
            }

            JsonNode embedding = embeddings.get(0).path("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;

        } catch (Exception e) {
            log.error("Failed to call multimodal embedding API", e);
            throw new RuntimeException("Multimodal embedding failed: " + e.getMessage(), e);
        }
    }
}
```

---

### Task 3: ProductImageService — 产品索引与搜索

**Files:**
- Create: `src/main/java/com/smartwealth/ai/service/ProductImageService.java`

**Interfaces:**
- Consumes: `ProductImageRepository` (Task 1), `@Qualifier("imageVectorStore") VectorStore` (Task 1), `ImageEmbeddingService` (Task 2), `ImageSearchProperties` (Task 1)
- Produces: `indexProduct(...)`, `search(...)`, `listProducts(...)`, `deleteProduct(...)` — consumed by Task 4

- [ ] **Step 1: 创建 ProductImageService**

```java
// src/main/java/com/smartwealth/ai/service/ProductImageService.java
package com.smartwealth.ai.service;

import com.smartwealth.ai.config.ImageSearchConfig.ImageSearchProperties;
import com.smartwealth.ai.domain.ProductImage;
import com.smartwealth.ai.repository.ProductImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductImageService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);

    private final ProductImageRepository repository;
    private final VectorStore imageVectorStore;
    private final ImageEmbeddingService embeddingService;
    private final ImageSearchProperties props;

    public ProductImageService(ProductImageRepository repository,
                               @Qualifier("imageVectorStore") VectorStore imageVectorStore,
                               ImageEmbeddingService embeddingService,
                               ImageSearchProperties props) {
        this.repository = repository;
        this.imageVectorStore = imageVectorStore;
        this.embeddingService = embeddingService;
        this.props = props;
    }

    @Transactional
    public ProductImage indexProduct(String tenantId, String productName, String category,
                                     String description, byte[] imageBytes, String contentType) {
        // 1. Save image file to disk
        Path tenantDir = Paths.get(props.getUploadDir(), tenantId);
        try {
            Files.createDirectories(tenantDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload dir", e);
        }

        ProductImage entity = new ProductImage();
        entity.setTenantId(tenantId);
        entity.setProductName(productName);
        entity.setCategory(category);
        entity.setDescription(description);
        entity.setImageContentType(contentType);
        entity = repository.save(entity);

        String imagePath = tenantDir.resolve(entity.getId() + ".jpg").toString();
        try {
            Files.write(Paths.get(imagePath), imageBytes);
        } catch (IOException e) {
            repository.delete(entity);
            throw new RuntimeException("Failed to save image file", e);
        }

        entity.setImagePath(imagePath);
        entity = repository.save(entity);

        // 2. Get multimodal embedding
        float[] embedding = embeddingService.embed(imageBytes);

        // 3. Build searchable text for metadata (what gets stored alongside vector)
        String searchableText = "Product: " + productName
                + " | Category: " + (category != null ? category : "")
                + " | Description: " + (description != null ? description : "");

        // 4. Store in vector store
        String vectorId = UUID.nameUUIDFromBytes(
                (tenantId + "-product-" + entity.getId()).getBytes()).toString();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenantId", tenantId);
        metadata.put("productId", entity.getId());
        metadata.put("productName", productName);
        metadata.put("category", category);
        metadata.put("description", description);

        List<org.springframework.ai.document.Document> docs = List.of(
                new org.springframework.ai.document.Document(vectorId, searchableText, metadata));
        // Note: Spring AI's VectorStore.add() uses the embedding model internally.
        // Since we need to use our own custom embedding, we need to use PgVectorStore directly.
        // Workaround: store the embedding manually via JdbcTemplate.
        // SEE STEP 2 below for the actual storage approach.

        entity.setVectorId(vectorId);
        repository.save(entity);

        log.info("Indexed product '{}' (id={}) for tenant={}", productName, entity.getId(), tenantId);
        return entity;
    }

    @Transactional
    public void deleteProduct(String tenantId, Long productId) {
        ProductImage entity = repository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));

        // Delete from vector store
        if (entity.getVectorId() != null) {
            imageVectorStore.delete(List.of(entity.getVectorId()));
        }

        // Delete image file
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

    public List<ProductImage> listProducts(String tenantId, String category) {
        if (category != null && !category.isBlank()) {
            return repository.findByTenantIdAndCategoryOrderByUploadTimeDesc(tenantId, category);
        }
        return repository.findByTenantIdOrderByUploadTimeDesc(tenantId);
    }

    public Optional<ProductImage> getProduct(String tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId);
    }
}
```

- [ ] **Step 2: 解决 VectorStore 自定义 embedding 存储问题**

Spring AI 的 VectorStore.add() 默认用 Spring AI 配置的 EmbeddingModel 来向量化，但我们需要用百炼多模态 embedding。解决方案：

```java
// 在 ProductImageService 中，indexProduct 方法里：
// 直接往 pgvector 表写入向量（绕过 VectorStore.add 的 auto-embedding）

import org.springframework.jdbc.core.JdbcTemplate;

// 在构造函数中注入 JdbcTemplate
private final JdbcTemplate jdbc;

// 在 indexProduct 中，获得 embedding 后：
jdbc.update(
    "INSERT INTO vector_store_image (id, content, metadata, embedding) VALUES (?, ?, ?, ?::vector)",
    vectorId,
    searchableText,
    objectMapper.writeValueAsString(metadata),
    vectorToString(embedding)
);

// vectorToString helper:
private static String vectorToString(float[] v) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < v.length; i++) {
        if (i > 0) sb.append(",");
        sb.append(v[i]);
    }
    sb.append("]");
    return sb.toString();
}
```

对于搜索，同样直接用 SQL：

```java
public List<ProductSearchResult> search(String tenantId, byte[] queryImageBytes, int topK) {
    float[] queryEmbedding = embeddingService.embed(queryImageBytes);
    String vectorStr = vectorToString(queryEmbedding);

    String sql = """
        SELECT id, content, metadata, 1 - (embedding <=> ?::vector) AS similarity
        FROM vector_store_image
        WHERE (metadata ->> 'tenantId') = ?
        ORDER BY embedding <=> ?::vector
        LIMIT ?
        """;

    return jdbc.query(sql,
            ps -> {
                ps.setString(1, vectorStr);
                ps.setString(2, tenantId);
                ps.setString(3, vectorStr);
                ps.setInt(4, topK);
            },
            (rs, rowNum) -> {
                double similarity = rs.getDouble("similarity");
                String metaJson = rs.getString("metadata");
                // Parse metadata JSON...
                return new ProductSearchResult(
                    Long.parseLong(parseJson(metaJson, "productId")),
                    parseJson(metaJson, "productName"),
                    parseJson(metaJson, "category"),
                    parseJson(metaJson, "description"),
                    similarity
                );
            });
}

public record ProductSearchResult(Long productId, String productName, String category,
                                   String description, double similarity) {}
```

> **Note:** 这里直接用 JdbcTemplate 操作 pgvector 原生 SQL，因为 Spring AI 的 VectorStore 接口假设使用 EmbeddingModel，不走自定义 embedding 管道。原生 SQL 也是 pgvector 推荐的最灵活用法。

---

### Task 4: ProductImageController — REST API

**Files:**
- Create: `src/main/java/com/smartwealth/ai/api/ProductImageController.java`

**Interfaces:**
- Consumes: `ProductImageService` (Task 3)
- Produces: REST endpoints consumed by frontend (Tasks 5, 6)

- [ ] **Step 1: 创建 ProductImageController**

```java
// src/main/java/com/smartwealth/ai/api/ProductImageController.java
package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.ProductImage;
import com.smartwealth.ai.service.ProductImageService;
import com.smartwealth.ai.service.ProductImageService.ProductSearchResult;
import com.smartwealth.ai.tenant.TenantContext;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/products")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("image") MultipartFile file,
            @RequestParam("name") String productName,
            @RequestParam(value = "category", required = false, defaultValue = "") String category,
            @RequestParam(value = "description", required = false, defaultValue = "") String description) {

        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        try {
            ProductImage product = productImageService.indexProduct(
                    tenantId, productName.trim(), category.trim(), description.trim(),
                    file.getBytes(), file.getContentType());

            return ResponseEntity.ok(Map.of(
                    "message", "Product uploaded",
                    "id", product.getId(),
                    "name", product.getProductName(),
                    "category", product.getCategory(),
                    "description", product.getDescription()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestParam("image") MultipartFile file) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        try {
            List<ProductSearchResult> results = productImageService.search(tenantId, file.getBytes(), 5);
            return ResponseEntity.ok(Map.of("results", results));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "category", required = false) String category) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        List<ProductImage> products = productImageService.listProducts(tenantId, category);
        List<Map<String, Object>> result = products.stream().map(p -> Map.of(
                "id", (Object) p.getId(),
                "name", p.getProductName(),
                "category", p.getCategory() != null ? p.getCategory() : "",
                "description", p.getDescription() != null ? p.getDescription() : "",
                "uploadTime", p.getUploadTime() != null ? p.getUploadTime().toString() : ""
        )).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().build();
        }

        ProductImage product = productImageService.getProduct(tenantId, id).orElse(null);
        if (product == null || product.getImagePath() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] bytes = Files.readAllBytes(Paths.get(product.getImagePath()));
            MediaType mediaType = MediaType.parseMediaType(
                    product.getImageContentType() != null ? product.getImageContentType() : "image/jpeg");
            return ResponseEntity.ok().contentType(mediaType).body(bytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        try {
            productImageService.deleteProduct(tenantId, id);
            return ResponseEntity.ok(Map.of("message", "Product deleted"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
```

---

### Task 5: 前端 — 方案 B 产品库页面 + Q&A 导航切换

**Files:**
- Rewrite: `src/main/resources/static/index.html`

**Interfaces:**
- Consumes: `GET /api/products`, `POST /api/products/upload`, `DELETE /api/products/{id}`, `GET /api/products/{id}/image`, `POST /api/chat/completions`, `GET /api/documents`, `POST /api/documents/upload`, `DELETE /api/documents/{name}`

- [ ] **Step 1: 基于 `test_files/demo_image_search_b.html` 重构为生产版本**

核心改动：
1. 接入真实 API（X-Tenant-ID header、admin key）
2. 产品网格显示真实缩略图（`GET /api/products/{id}/image`）
3. 上传弹窗调真实 API
4. 删除功能调真实 API
5. 品类筛选动态化（从 `/api/products` 结果中聚合）
6. 搜索框支持文字过滤

前端文件过大 (~900 行)，关键修改点：

```html
<!-- Navigation -->
<nav class="nav">
  <button class="nav-item active" onclick="switchPage('qa')">💬 智能问答</button>
  <button class="nav-item" onclick="switchPage('products')">🏷️ 产品库</button>
</nav>
```

```javascript
// API helpers
function apiHeaders() {
  const h = { 'X-Tenant-ID': currentTenant };
  if (isAdmin && adminKey) h['X-Admin-Key'] = adminKey;
  return h;
}

async function uploadProduct(file, name, category, description) {
  const formData = new FormData();
  formData.append('image', file);
  formData.append('name', name);
  if (category) formData.append('category', category);
  if (description) formData.append('description', description);

  const resp = await fetch('/api/products/upload', {
    method: 'POST',
    headers: { 'X-Tenant-ID': currentTenant, ...(isAdmin && adminKey ? {'X-Admin-Key': adminKey} : {}) },
    body: formData
  });
  if (!resp.ok) { const err = await resp.json(); throw new Error(err.error); }
  return resp.json();
}

async function loadProducts() {
  const resp = await fetch('/api/products', { headers: apiHeaders() });
  return resp.json();
}

async function deleteProduct(id) {
  const resp = await fetch('/api/products/' + id, { method: 'DELETE', headers: apiHeaders() });
  if (!resp.ok) { const err = await resp.json(); throw new Error(err.error); }
}

// Thumbnail URL
function productImageUrl(id) {
  return '/api/products/' + id + '/image';
}
```

- [ ] **Step 2: 在产品库页面中加入上传弹窗**
  - 上传区支持拖拽
  - 必填：产品名称
  - 选填：品类、描述
  - 上传进度条

- [ ] **Step 3: 保持现有 Q&A 功能不变**
  - 侧边栏文档管理保持原样
  - 聊天框功能保持原样
  - 只在外层加导航

---

### Task 6: 聊天框拍照搜索集成

**Files:**
- Modify: `src/main/resources/static/index.html` (Q&A page chat input area)
- Modify: `src/main/java/com/smartwealth/ai/api/ChatController.java` (optional, for server-side image search orchestration)

**Interfaces:**
- Consumes: `POST /api/products/search` (multipart image)
- Frontend chat area adds 📷 button

- [ ] **Step 1: 聊天框加 📷 按钮**

```html
<div class="chat-input-area">
  <button class="btn-img" id="imgSearchBtn" title="拍照搜产品" onclick="triggerImageSearch()">📷</button>
  <input type="text" id="chatInput" placeholder="输入问题，或点击 📷 拍照搜索产品...">
  <button id="sendBtn" onclick="sendMessage()">Send</button>
</div>
<input type="file" id="imageSearchInput" accept="image/*" capture="environment" style="display:none"
       onchange="handleImageSearch(this.files[0])">
```

- [ ] **Step 2: 拍照搜索逻辑**

```javascript
function triggerImageSearch() {
  document.getElementById('imageSearchInput').click();
}

async function handleImageSearch(file) {
  if (!file) return;
  if (!currentTenant) { alert('Please select an enterprise first'); return; }

  // Show user message with preview
  const reader = new FileReader();
  reader.onload = function(e) {
    appendMessage('user', `<img src="${e.target.result}" style="max-width:200px;border-radius:8px;"><br><small>📷 Photo search</small>`);
  };
  reader.readAsDataURL(file);

  // Show loading
  const loadingDiv = appendMessage('assistant', '<span class="spinner"></span> Searching similar products...');

  const formData = new FormData();
  formData.append('image', file);

  try {
    const resp = await fetch('/api/products/search', {
      method: 'POST',
      headers: apiHeaders(),
      body: formData
    });
    const data = await resp.json();
    loadingDiv.remove();

    if (!resp.ok) {
      appendMessage('error', 'Search failed: ' + (data.error || resp.status));
      return;
    }

    let html = '<b>🔍 Similar products found:</b><br><br>';
    html += '<div class="chat-img-msg"><div class="result-list">';
    data.results.forEach((r, i) => {
      html += `<div class="result-row">
        <div class="thumb-small" style="background-image:url(${productImageUrl(r.productId)})"></div>
        <div>
          <div style="font-weight:600;">${escapeHtml(r.productName)}</div>
          <div style="font-size:11px;color:var(--text-muted);">${escapeHtml(r.category || '')} · ${escapeHtml((r.description || '').substring(0, 60))}</div>
        </div>
        <span class="match">${Math.round(r.similarity * 100)}%</span>
      </div>`;
    });
    html += '</div></div>';
    appendMessage('assistant', html);
  } catch (err) {
    loadingDiv.remove();
    appendMessage('error', 'Network error: ' + err.message);
  }
}
```

---

### Task 7: 端到端联调

**Files:** (none — verification only)

- [ ] **Step 1: 编译启动**

```bash
mvn clean compile
mvn spring-boot:run
```

- [ ] **Step 2: 测试产品上传**

```bash
curl -X POST http://localhost:8080/api/products/upload \
  -H "X-Tenant-ID: light_shop" \
  -F "image=@/path/to/chandelier.jpg" \
  -F "name=北欧吊灯 P302" \
  -F "category=吊灯" \
  -F "description=黑色金属灯体，E27螺口"
```

Expected: `{"message":"Product uploaded","id":1,...}`

- [ ] **Step 3: 测试产品列表**

```bash
curl http://localhost:8080/api/products -H "X-Tenant-ID: light_shop"
```

Expected: JSON array with product metadata

- [ ] **Step 4: 测试图片搜索**

```bash
curl -X POST http://localhost:8080/api/products/search \
  -H "X-Tenant-ID: light_shop" \
  -F "image=@/path/to/query_chandelier.jpg"
```

Expected: `{"results":[{"productId":1,"productName":"北欧吊灯 P302","similarity":0.92},...]}`

- [ ] **Step 5: 浏览器验证**
  - 打开 `http://localhost:8080`
  - 切换到产品库页面，验证缩略图网格
  - 上传 3-5 张产品照片
  - 切换到 Q&A 页面，点击 📷 拍照搜索
  - 验证搜索结果正确展示
  - 验证删除功能
  - 验证品类筛选

- [ ] **Step 6: 多租户验证**
  - 从 `light_shop` 上传产品
  - 切换到另一个租户，确认看不到 `light_shop` 的产品

---

## 验证清单

| 验证项 | 方法 |
|---|---|
| 产品上传 API | curl POST /api/products/upload |
| 产品列表 API | curl GET /api/products |
| 图片搜索 API | curl POST /api/products/search |
| 产品图片服务 | curl GET /api/products/1/image |
| 产品删除 API | curl DELETE /api/products/1 |
| 产品库页面 | 浏览器打开，检查网格渲染、上传弹窗、删除功能 |
| 拍照搜索 | Q&A 页面点 📷 上传照片，检查搜索结果 |
| 多租户隔离 | 切换租户，确认产品列表隔离 |
| 品类筛选 | 产品库页面选择品类，检查过滤正确性 |
| 文档功能 | 确认现有文档上传/Q&A 不受影响 |
