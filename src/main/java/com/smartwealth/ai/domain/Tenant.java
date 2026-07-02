package com.smartwealth.ai.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", unique = true, nullable = false, length = 50)
    private String tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String config;

    @Column(name = "tenant_group", nullable = false, length = 20)
    private String tenantGroup = "enterprise";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public String getTenantGroup() { return tenantGroup; }
    public void setTenantGroup(String tenantGroup) { this.tenantGroup = tenantGroup; }
}
