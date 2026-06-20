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
