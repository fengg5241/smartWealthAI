package com.smartwealth.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "im_tenant_mapping")
public class ImTenantMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(name = "platform_team_id", nullable = false, length = 200)
    private String platformTeamId;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "bot_token", length = 255)
    private String botToken;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public ImTenantMapping() {}

    public ImTenantMapping(String platform, String platformTeamId, String tenantId) {
        this.platform = platform;
        this.platformTeamId = platformTeamId;
        this.tenantId = tenantId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getPlatformTeamId() { return platformTeamId; }
    public void setPlatformTeamId(String platformTeamId) { this.platformTeamId = platformTeamId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
