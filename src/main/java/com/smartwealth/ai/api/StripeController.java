package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.Tenant;
import com.smartwealth.ai.repository.TenantRepository;
import com.smartwealth.ai.tenant.TenantContext;
import com.stripe.Stripe;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stripe")
public class StripeController {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final TenantRepository tenantRepository;
    private final String secretKey;
    private final String monthlyPriceId;
    private final String annualPriceId;
    private final String webhookSecret;
    private final String domain;
    private final String pricingCurrency;
    private final int trialDays;
    private final double monthlyAmount;
    private final double annualAmount;
    private final String monthlyLabel;
    private final String annualLabel;

    public StripeController(TenantRepository tenantRepository,
                            @Value("${stripe.secret-key}") String secretKey,
                            @Value("${stripe.webhook-secret}") String webhookSecret,
                            @Value("${stripe.domain}") String domain,
                            @Value("${stripe.pricing.currency:SGD}") String pricingCurrency,
                            @Value("${stripe.pricing.trial-days:7}") int trialDays,
                            @Value("${stripe.pricing.monthly.price-id}") String monthlyPriceId,
                            @Value("${stripe.pricing.monthly.amount:5.90}") double monthlyAmount,
                            @Value("${stripe.pricing.monthly.label:Monthly}") String monthlyLabel,
                            @Value("${stripe.pricing.annual.price-id}") String annualPriceId,
                            @Value("${stripe.pricing.annual.amount:59.00}") double annualAmount,
                            @Value("${stripe.pricing.annual.label:Annual}") String annualLabel) {
        this.tenantRepository = tenantRepository;
        this.secretKey = secretKey;
        this.monthlyPriceId = monthlyPriceId;
        this.annualPriceId = annualPriceId;
        this.webhookSecret = webhookSecret;
        this.domain = domain;
        this.pricingCurrency = pricingCurrency;
        this.trialDays = trialDays;
        this.monthlyAmount = monthlyAmount;
        this.annualAmount = annualAmount;
        this.monthlyLabel = monthlyLabel;
        this.annualLabel = annualLabel;
        Stripe.apiKey = secretKey;
    }

    @GetMapping("/pricing")
    public ResponseEntity<Map<String, Object>> pricing() {
        return ResponseEntity.ok(Map.of(
                "currency", pricingCurrency,
                "trialDays", trialDays,
                "plans", List.of(
                        Map.of("id", "monthly", "label", monthlyLabel,
                                "amount", monthlyAmount, "interval", "month"),
                        Map.of("id", "annual", "label", annualLabel,
                                "amount", annualAmount, "interval", "year")
                )));
    }

    @PostMapping("/create-checkout")
    public ResponseEntity<?> createCheckout(@RequestBody(required = false) Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }
        Tenant tenant = tenantRepository.findByTenantId(tenantId).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Tenant not found"));
        }

        String plan = "monthly";
        if (body != null && body.get("plan") instanceof String p && !p.isBlank()) {
            plan = p;
        }
        String priceId = "annual".equals(plan) ? annualPriceId : monthlyPriceId;

        try {
            SessionCreateParams.Builder builder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(domain + "/index.html?tenant=" + tenantId + "&subscribed=true")
                    .setCancelUrl(domain + "/index.html?tenant=" + tenantId + "&cancelled=true")
                    .putMetadata("tenant_id", tenantId)
                    .putMetadata("plan", plan)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPrice(priceId)
                            .build());

            if (tenant.getStripeCustomerId() != null) {
                builder.setCustomer(tenant.getStripeCustomerId());
            } else if (tenant.getEmail() != null) {
                builder.setCustomerEmail(tenant.getEmail());
            }

            if (!tenant.isSubscriptionActive() && tenant.getSubscriptionExpiry() == null) {
                builder.setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .setTrialPeriodDays((long) trialDays)
                        .build());
            }

            com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(builder.build());
            return ResponseEntity.ok(Map.of("url", session.getUrl()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create checkout: " + e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody String payload,
                                     @RequestHeader("Stripe-Signature") String sigHeader) {
        System.err.println("=== STRIPE WEBHOOK RECEIVED, payload length=" + payload.length());
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.ok(Map.of("received", true));
        }

        try {
            // Verify signature
            com.stripe.model.Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            String eventType = event.getType();
            System.err.println("=== WEBHOOK EVENT TYPE: " + eventType);

            if ("checkout.session.completed".equals(eventType)) {
                // Parse JSON manually instead of relying on SDK deserialization
                var root = mapper.readTree(payload);
                var obj = root.path("data").path("object");
                var metadata = obj.path("metadata");
                String tenantId = metadata.has("tenant_id") ? metadata.get("tenant_id").asText() : null;
                String plan = metadata.has("plan") ? metadata.get("plan").asText() : null;
                String customerId = obj.has("customer") ? obj.get("customer").asText() : null;
                String subscriptionId = obj.has("subscription") ? obj.get("subscription").asText() : null;
                System.err.println("=== tenantId=" + tenantId + " cust=" + customerId + " sub=" + subscriptionId + " plan=" + plan);

                if (tenantId != null) {
                    String finalCustomerId = customerId;
                    String finalSubscriptionId = subscriptionId;
                    String finalPlan = plan;
                    tenantRepository.findByTenantId(tenantId).ifPresent(tenant -> {
                        if (finalCustomerId != null) tenant.setStripeCustomerId(finalCustomerId);
                        if (finalSubscriptionId != null) tenant.setStripeSubscriptionId(finalSubscriptionId);
                        if (finalPlan != null) tenant.setSubscriptionPlan(finalPlan);
                        try {
                            if (finalSubscriptionId != null) {
                                Subscription sub = Subscription.retrieve(finalSubscriptionId);
                                tenant.setSubscriptionExpiry(LocalDate.ofInstant(
                                        Instant.ofEpochSecond(sub.getCurrentPeriodEnd()),
                                        ZoneId.systemDefault()));
                            }
                        } catch (Exception e) {
                            tenant.setSubscriptionExpiry(LocalDate.now().plusDays(30));
                        }
                        tenantRepository.save(tenant);
                        System.err.println("=== TENANT SAVED OK, expiry=" + tenant.getSubscriptionExpiry());
                    });
                }
            }
            return ResponseEntity.ok(Map.of("received", true));
        } catch (Exception e) {
            System.err.println("=== WEBHOOK FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }
}
