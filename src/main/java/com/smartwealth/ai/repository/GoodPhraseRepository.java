package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.GoodPhrase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface GoodPhraseRepository extends JpaRepository<GoodPhrase, Long> {
    List<GoodPhrase> findByTenantIdOrderByCreatedTimeDesc(String tenantId);
    List<GoodPhrase> findByTenantIdAndNotebookIdOrderByCreatedTimeDesc(String tenantId, Long notebookId);
    Optional<GoodPhrase> findByIdAndTenantId(Long id, String tenantId);

    @Query("SELECT g FROM GoodPhrase g WHERE g.tenantId = :tenantId " +
           "AND (:theme IS NULL OR g.theme = :theme) " +
           "AND (:emotion IS NULL OR g.emotion = :emotion) " +
           "AND (:masteryLevel IS NULL OR g.masteryLevel = :masteryLevel) " +
           "ORDER BY g.createdTime DESC")
    List<GoodPhrase> findByFilters(@Param("tenantId") String tenantId,
                                   @Param("theme") String theme,
                                   @Param("emotion") String emotion,
                                   @Param("masteryLevel") String masteryLevel);

    @Query("SELECT DISTINCT g.theme FROM GoodPhrase g WHERE g.tenantId = :tenantId AND g.theme IS NOT NULL AND g.theme <> '' ORDER BY g.theme")
    List<String> findDistinctThemes(@Param("tenantId") String tenantId);

    @Query("SELECT DISTINCT g.emotion FROM GoodPhrase g WHERE g.tenantId = :tenantId AND g.emotion IS NOT NULL AND g.emotion <> '' ORDER BY g.emotion")
    List<String> findDistinctEmotions(@Param("tenantId") String tenantId);
}
