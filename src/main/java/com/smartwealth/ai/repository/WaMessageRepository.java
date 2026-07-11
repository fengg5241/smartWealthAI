package com.smartwealth.ai.repository;

import com.smartwealth.ai.domain.WaMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WaMessageRepository extends JpaRepository<WaMessage, Long> {
    java.util.List<WaMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    org.springframework.data.domain.Page<WaMessage> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            Long conversationId, java.time.LocalDateTime before, org.springframework.data.domain.Pageable pageable);
    Optional<WaMessage> findByProviderMessageId(String providerMessageId);
    java.util.List<WaMessage> findByIdGreaterThanAndConversationIdInOrderByIdAsc(
            Long lastId, java.util.List<Long> conversationIds);
}
