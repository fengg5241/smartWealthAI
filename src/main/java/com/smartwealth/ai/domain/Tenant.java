package com.smartwealth.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "stripe_customer_id", length = 100)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 100)
    private String stripeSubscriptionId;

    @Column(name = "subscription_expiry")
    private LocalDate subscriptionExpiry;

    @Column(name = "trial_ends_at")
    private LocalDate trialEndsAt;

    @Column(name = "reset_token", length = 100)
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @Column(name = "subscription_plan", length = 20)
    private String subscriptionPlan;

    @Column(name = "cancel_at_period_end")
    private Boolean cancelAtPeriodEnd = false;

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

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }

    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }

    public LocalDate getSubscriptionExpiry() { return subscriptionExpiry; }
    public void setSubscriptionExpiry(LocalDate subscriptionExpiry) { this.subscriptionExpiry = subscriptionExpiry; }

    public LocalDate getTrialEndsAt() { return trialEndsAt; }
    public void setTrialEndsAt(LocalDate trialEndsAt) { this.trialEndsAt = trialEndsAt; }

    public boolean isSubscriptionActive() {
        return subscriptionExpiry != null && !subscriptionExpiry.isBefore(LocalDate.now());
    }

    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }

    public LocalDateTime getResetTokenExpiry() { return resetTokenExpiry; }
    public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) { this.resetTokenExpiry = resetTokenExpiry; }

    public String getSubscriptionPlan() { return subscriptionPlan; }
    public void setSubscriptionPlan(String subscriptionPlan) { this.subscriptionPlan = subscriptionPlan; }

    public Boolean getCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public void setCancelAtPeriodEnd(Boolean cancelAtPeriodEnd) { this.cancelAtPeriodEnd = cancelAtPeriodEnd; }
}
