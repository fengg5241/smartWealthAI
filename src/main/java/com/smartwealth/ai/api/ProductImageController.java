package com.smartwealth.ai.api;

import com.aliyun.oss.OSS;
import com.smartwealth.ai.config.OssConfig;
import com.smartwealth.ai.domain.ProductImage;
import com.smartwealth.ai.service.ProductImageService;
import com.smartwealth.ai.service.ProductImageService.ProductSearchResult;
import com.smartwealth.ai.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

@RestController
@RequestMapping("/api/products")
public class ProductImageController {

    private static final Logger log = LoggerFactory.getLogger(ProductImageController.class);

    private final ProductImageService productImageService;
    private final VisionDescriptionService visionService;
    private final OSS ossClient;
    private final OssConfig.OssProperties ossProperties;

    public ProductImageController(ProductImageService productImageService,
                                  VisionDescriptionService visionService,
                                  OSS ossClient,
                                  OssConfig.OssProperties ossProperties) {
        this.productImageService = productImageService;
        this.visionService = visionService;
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    @PostMapping("/describe")
    public ResponseEntity<Map<String, Object>> describeImage(@RequestParam("image") MultipartFile file) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        try {
            String description = visionService.describeImage(file.getBytes(), file.getContentType());
            return ResponseEntity.ok(Map.of("description", description));
        } catch (Exception e) {
            log.error("Image description failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("image") MultipartFile file,
            @RequestParam(value = "name", required = false, defaultValue = "") String productName,
            @RequestParam(value = "category", required = false, defaultValue = "") String category,
            @RequestParam(value = "description", required = false, defaultValue = "") String description) {

        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        String name = productName.trim().isBlank() ? file.getOriginalFilename() : productName.trim();

        try {
            byte[] imageBytes = file.getBytes();
            String contentType = file.getContentType();

            String desc = description != null ? description.trim() : "";
            if (desc.isBlank()) {
                desc = visionService.describeImage(imageBytes, contentType);
            }

            ProductImage product = productImageService.indexProduct(
                    tenantId, name, category.trim(), desc,
                    imageBytes, contentType);

            return ResponseEntity.ok(Map.of(
                    "message", "Product uploaded",
                    "id", product.getId(),
                    "name", product.getProductName(),
                    "category", product.getCategory(),
                    "description", product.getDescription()
            ));
        } catch (Exception e) {
            log.error("Product upload failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestParam("image") MultipartFile file,
                                    @RequestParam(value = "topK", required = false, defaultValue = "5") int topK) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        try {
            List<ProductSearchResult> results = productImageService.search(tenantId, file.getBytes(), topK);
            return ResponseEntity.ok(Map.of("results", results));
        } catch (Exception e) {
            log.error("Product search failed", e);
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
        List<String> categories = productImageService.getCategories(tenantId);

        List<Map<String, Object>> result = products.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getProductName());
            m.put("category", p.getCategory() != null ? p.getCategory() : "");
            m.put("description", p.getDescription() != null ? p.getDescription() : "");
            m.put("uploadTime", p.getUploadTime() != null ? p.getUploadTime().toString() : "");
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of("products", result, "categories", categories));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id,
                                           @RequestParam(value = "tid", required = false) String queryTenant) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            tenantId = queryTenant;
        }
        if (tenantId == null) {
            return ResponseEntity.badRequest().build();
        }

        ProductImage product = productImageService.getProduct(tenantId, id).orElse(null);
        if (product == null || product.getImagePath() == null) {
            return ResponseEntity.notFound().build();
        }

        try (InputStream is = ossClient.getObject(ossProperties.getBucket(), product.getImagePath()).getObjectContent();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[8192];
            int n;
            while ((n = is.read(data)) != -1) buffer.write(data, 0, n);
            byte[] bytes = buffer.toByteArray();
            MediaType mediaType = MediaType.parseMediaType(
                    product.getImageContentType() != null ? product.getImageContentType() : "image/jpeg");
            return ResponseEntity.ok().contentType(mediaType).body(bytes);
        } catch (Exception e) {
            log.error("Failed to read image from OSS: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                       @RequestBody Map<String, String> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        try {
            ProductImage updated = productImageService.updateProduct(tenantId, id,
                    body.get("name"), body.get("category"), body.get("description"));
            return ResponseEntity.ok(Map.of(
                    "id", updated.getId(),
                    "name", updated.getProductName(),
                    "category", updated.getCategory(),
                    "description", updated.getDescription()
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
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
