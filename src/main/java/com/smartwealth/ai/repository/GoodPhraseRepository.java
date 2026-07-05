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
           "AND (:language IS NULL OR g.language = :language) " +
           "AND (:notebookId IS NULL OR g.notebookId = :notebookId) " +
           "ORDER BY g.createdTime DESC")
    List<GoodPhrase> findByFilters(@Param("tenantId") String tenantId,
                                   @Param("theme") String theme,
                                   @Param("emotion") String emotion,
                                   @Param("masteryLevel") String masteryLevel,
                                   @Param("language") String language,
                                   @Param("notebookId") Long notebookId);

    @Query("SELECT DISTINCT g.theme FROM GoodPhrase g WHERE g.tenantId = :tenantId AND g.theme IS NOT NULL AND g.theme <> '' ORDER BY g.theme")
    List<String> findDistinctThemes(@Param("tenantId") String tenantId);

    @Query("SELECT DISTINCT g.emotion FROM GoodPhrase g WHERE g.tenantId = :tenantId AND g.emotion IS NOT NULL AND g.emotion <> '' ORDER BY g.emotion")
    List<String> findDistinctEmotions(@Param("tenantId") String tenantId);

    @Query(value = """
        SELECT DISTINCT TRIM(t.tag) FROM good_phrase g,
        LATERAL unnest(string_to_array(g.tags, ',')) AS t(tag)
        WHERE g.tenant_id = :tenantId
        AND g.tags IS NOT NULL
        AND TRIM(t.tag) <> ''
        ORDER BY TRIM(t.tag)
        """, nativeQuery = true)
    List<String> findDistinctTags(@Param("tenantId") String tenantId);

    @Query("SELECT DISTINCT g.language FROM GoodPhrase g WHERE g.tenantId = :tenantId AND g.language IS NOT NULL AND g.language <> '' ORDER BY g.language")
    List<String> findDistinctLanguages(@Param("tenantId") String tenantId);
}
