package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.WaConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaConversationRepository extends JpaRepository<WaConversation, Long> {

    Optional<WaConversation> findFirstByTenantIdAndCustomerIdAndStatusInOrderByCreatedAtDesc(
            String tenantId, Long customerId, List<String> statuses);

    List<WaConversation> findByTenantIdAndStatusNotOrderByUpdatedAtDesc(String tenantId, String status);

    Optional<WaConversation> findByIdAndTenantId(Long id, String tenantId);

    /** Optimistic lock takeover: only succeeds if status is ai_active or pending_human */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE WaConversation c SET c.status = 'human_assigned', c.assignedAgent = :agent, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id AND c.tenantId = :tenantId AND c.status IN ('ai_active', 'pending_human')")
    int takeOver(@Param("id") Long id, @Param("tenantId") String tenantId, @Param("agent") String agent);

    /** Release a conversation back to AI. Only the assigned agent can release. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE WaConversation c SET c.status = 'ai_active', c.assignedAgent = NULL, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id AND c.tenantId = :tenantId AND c.status = 'human_assigned' AND c.assignedAgent = :agent")
    int release(@Param("id") Long id, @Param("tenantId") String tenantId, @Param("agent") String agent);
}
