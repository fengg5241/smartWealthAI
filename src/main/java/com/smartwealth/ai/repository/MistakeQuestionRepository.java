package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.MistakeQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MistakeQuestionRepository extends JpaRepository<MistakeQuestion, Long> {
    List<MistakeQuestion> findByTenantIdOrderByCreatedTimeDesc(String tenantId);
    List<MistakeQuestion> findByTenantIdAndNotebookIdOrderByCreatedTimeDesc(String tenantId, Long notebookId);
    Optional<MistakeQuestion> findByIdAndTenantId(Long id, String tenantId);

    @Query("SELECT m FROM MistakeQuestion m WHERE m.tenantId = :tenantId " +
           "AND (:notebookId IS NULL OR m.notebookId = :notebookId) " +
           "AND (:subject IS NULL OR m.subject = :subject) " +
           "AND (:questionType IS NULL OR m.questionType = :questionType) " +
           "AND (:gradeLevel IS NULL OR m.gradeLevel = :gradeLevel) " +
           "AND (:masteryLevel IS NULL OR m.masteryLevel = :masteryLevel) " +
           "ORDER BY m.createdTime DESC")
    List<MistakeQuestion> findByFilters(@Param("tenantId") String tenantId,
                                        @Param("notebookId") Long notebookId,
                                        @Param("subject") String subject,
                                        @Param("questionType") String questionType,
                                        @Param("gradeLevel") String gradeLevel,
                                        @Param("masteryLevel") String masteryLevel);

    List<MistakeQuestion> findByTenantIdAndVectorIdIsNotNull(String tenantId);
}
