package com.smartwealth.ai.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wa_usage_log")
public class WaUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(name = "message_type", length = 20)
    private String messageType = "session";

    @Column(name = "twilio_message_sid", length = 100)
    private String twilioMessageSid;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(precision = 10, scale = 4)
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public WaUsageLog() {}

    public WaUsageLog(String tenantId, Long conversationId, String direction) {
        this.tenantId = tenantId;
        this.conversationId = conversationId;
        this.direction = direction;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getTwilioMessageSid() { return twilioMessageSid; }
    public void setTwilioMessageSid(String twilioMessageSid) { this.twilioMessageSid = twilioMessageSid; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
