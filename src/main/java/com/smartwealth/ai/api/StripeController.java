package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.Tenant;
import com.smartwealth.ai.repository.TenantRepository;
import com.smartwealth.ai.tenant.TenantContext;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
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

            // Add trial if tenant is within trial period
            if (!tenant.isSubscriptionActive() && tenant.getSubscriptionExpiry() == null) {
                builder.setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .setTrialPeriodDays((long) trialDays)
                        .build());
            }

            Session session = Session.create(builder.build());
            return ResponseEntity.ok(Map.of("url", session.getUrl()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create checkout: " + e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody String payload,
                                     @RequestHeader("Stripe-Signature") String sigHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.ok(Map.of("received", true));
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid signature"));
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session == null) break;
                String tenantId = session.getMetadata().get("tenant_id");
                String customerId = session.getCustomer();
                String subscriptionId = session.getSubscription();
                String plan = session.getMetadata().get("plan");
                if (tenantId != null) {
                    tenantRepository.findByTenantId(tenantId).ifPresent(tenant -> {
                        if (customerId != null) tenant.setStripeCustomerId(customerId);
                        if (subscriptionId != null) tenant.setStripeSubscriptionId(subscriptionId);
                        if (plan != null) tenant.setSubscriptionPlan(plan);
                        // Fetch actual period end from Stripe subscription
                        try {
                            if (subscriptionId != null) {
                                Subscription sub = Subscription.retrieve(subscriptionId);
                                long periodEnd = sub.getCurrentPeriodEnd();
                                tenant.setSubscriptionExpiry(
                                        LocalDate.ofInstant(Instant.ofEpochSecond(periodEnd), ZoneId.systemDefault()));
                            }
                        } catch (Exception e) {
                            // Fallback: 30 days from now
                            tenant.setSubscriptionExpiry(LocalDate.now().plusDays(30));
                        }
                        tenantRepository.save(tenant);
                    });
                }
            }
            case "invoice.paid" -> {
                handleInvoiceEvent(event, false);
            }
            case "invoice.payment_failed" -> {
                handleInvoiceEvent(event, true);
            }
            case "customer.subscription.deleted" -> {
                Subscription sub = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                if (sub == null) break;
                String customerId = sub.getCustomer();
                tenantRepository.findAll().stream()
                        .filter(t -> customerId.equals(t.getStripeCustomerId()))
                        .findFirst()
                        .ifPresent(tenant -> {
                            tenant.setStripeSubscriptionId(null);
                            tenantRepository.save(tenant);
                        });
            }
        }

        return ResponseEntity.ok(Map.of("received", true));
    }

    private void handleInvoiceEvent(Event event, boolean failed) {
        com.stripe.model.Invoice invoice =
                (com.stripe.model.Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
        if (invoice == null) return;
        String customerId = invoice.getCustomer();
        String subscriptionId = invoice.getSubscription();

        tenantRepository.findAll().stream()
                .filter(t -> subscriptionId != null && subscriptionId.equals(t.getStripeSubscriptionId()))
                .findFirst()
                .ifPresentOrElse(
                        tenant -> updateExpiryFromStripe(tenant, subscriptionId, failed),
                        () -> {
                            if (customerId != null) {
                                tenantRepository.findAll().stream()
                                        .filter(t -> customerId.equals(t.getStripeCustomerId()))
                                        .findFirst()
                                        .ifPresent(tenant -> updateExpiryFromStripe(tenant, subscriptionId, failed));
                            }
                        }
                );
    }

    private void updateExpiryFromStripe(Tenant tenant, String subscriptionId, boolean failed) {
        if (failed) {
            if (tenant.getSubscriptionExpiry() != null &&
                    tenant.getSubscriptionExpiry().isBefore(LocalDate.now())) {
                tenant.setSubscriptionExpiry(LocalDate.now());
            }
        } else {
            try {
                if (subscriptionId != null) {
                    Subscription sub = Subscription.retrieve(subscriptionId);
                    long periodEnd = sub.getCurrentPeriodEnd();
                    tenant.setSubscriptionExpiry(
                            LocalDate.ofInstant(Instant.ofEpochSecond(periodEnd), ZoneId.systemDefault()));
                } else {
                    // Fallback
                    LocalDate base = tenant.getSubscriptionExpiry();
                    if (base == null || base.isBefore(LocalDate.now())) {
                        base = LocalDate.now();
                    }
                    tenant.setSubscriptionExpiry(base.plusDays(30));
                }
            } catch (Exception e) {
                LocalDate base = tenant.getSubscriptionExpiry();
                if (base == null || base.isBefore(LocalDate.now())) {
                    base = LocalDate.now();
                }
                tenant.setSubscriptionExpiry(base.plusDays(30));
            }
        }
        tenantRepository.save(tenant);
    }
}
