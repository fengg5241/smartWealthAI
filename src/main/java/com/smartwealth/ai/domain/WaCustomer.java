package com.smartwealth.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "wa_customers")
public class WaCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "wa_phone", nullable = false, length = 30)
    private String waPhone;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(length = 500)
    private String tags;

    @Column(length = 100)
    private String budget;

    @Column(columnDefinition = "TEXT")
    private String requirement;

    @Column(length = 50)
    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public WaCustomer() {}

    public WaCustomer(String tenantId, String waPhone) {
        this.tenantId = tenantId;
        this.waPhone = waPhone;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getWaPhone() { return waPhone; }
    public void setWaPhone(String waPhone) { this.waPhone = waPhone; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
