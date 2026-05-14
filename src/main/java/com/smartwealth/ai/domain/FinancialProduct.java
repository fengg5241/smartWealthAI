package com.smartwealth.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_product")
public class FinancialProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, unique = true, length = 50)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(name = "supported_risk_level", nullable = false, length = 32)
    private RiskLevel supportedRiskLevel;

    @Column(name = "annual_return_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal annualReturnRate;

    @Column(name = "min_holding_days", nullable = false)
    private Integer minHoldingDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "liquidity_level", nullable = false, length = 32)
    private LiquidityLevel liquidityLevel;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "compliance_note", nullable = false, columnDefinition = "text")
    private String complianceNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public RiskLevel getSupportedRiskLevel() {
        return supportedRiskLevel;
    }

    public void setSupportedRiskLevel(RiskLevel supportedRiskLevel) {
        this.supportedRiskLevel = supportedRiskLevel;
    }

    public BigDecimal getAnnualReturnRate() {
        return annualReturnRate;
    }

    public void setAnnualReturnRate(BigDecimal annualReturnRate) {
        this.annualReturnRate = annualReturnRate;
    }

    public Integer getMinHoldingDays() {
        return minHoldingDays;
    }

    public void setMinHoldingDays(Integer minHoldingDays) {
        this.minHoldingDays = minHoldingDays;
    }

    public LiquidityLevel getLiquidityLevel() {
        return liquidityLevel;
    }

    public void setLiquidityLevel(LiquidityLevel liquidityLevel) {
        this.liquidityLevel = liquidityLevel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComplianceNote() {
        return complianceNote;
    }

    public void setComplianceNote(String complianceNote) {
        this.complianceNote = complianceNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
