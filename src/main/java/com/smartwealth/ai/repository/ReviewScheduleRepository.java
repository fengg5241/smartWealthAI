package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.ReviewSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, Long> {
    Optional<ReviewSchedule> findByTenantIdAndMistakeId(String tenantId, Long mistakeId);

    @Query("SELECT r FROM ReviewSchedule r WHERE r.tenantId = :tenantId AND r.nextReviewDate <= :today ORDER BY r.nextReviewDate ASC")
    List<ReviewSchedule> findDueReviews(@Param("tenantId") String tenantId, @Param("today") LocalDate today);

    @Query("SELECT COUNT(r) FROM ReviewSchedule r WHERE r.tenantId = :tenantId AND r.nextReviewDate <= :today")
    long countDueReviews(@Param("tenantId") String tenantId, @Param("today") LocalDate today);

    @Query("SELECT r FROM ReviewSchedule r WHERE r.tenantId = :tenantId ORDER BY r.nextReviewDate ASC")
    List<ReviewSchedule> findAllByTenantId(@Param("tenantId") String tenantId);
}
