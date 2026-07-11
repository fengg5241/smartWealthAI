package com.smartwealth.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "wa_conversations")
public class WaConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(length = 20)
    private String status = "ai_active";

    @Column(name = "assigned_agent", length = 100)
    private String assignedAgent;

    @Column(name = "last_customer_message_at")
    private LocalDateTime lastCustomerMessageAt;

    @Column(name = "unread_count")
    private int unreadCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public WaConversation() {}

    public WaConversation(String tenantId, Long customerId) {
        this.tenantId = tenantId;
        this.customerId = customerId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssignedAgent() { return assignedAgent; }
    public void setAssignedAgent(String assignedAgent) { this.assignedAgent = assignedAgent; }
    public LocalDateTime getLastCustomerMessageAt() { return lastCustomerMessageAt; }
    public void setLastCustomerMessageAt(LocalDateTime lastCustomerMessageAt) { this.lastCustomerMessageAt = lastCustomerMessageAt; }
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
