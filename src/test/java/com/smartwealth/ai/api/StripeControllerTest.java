package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.Tenant;
import com.smartwealth.ai.repository.TenantRepository;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StripeController.class)
@ActiveProfiles("test")
class StripeControllerTest {

    private static final String TEST_TENANT = "study_c95baed6";
    private static final String TEST_SUB_ID = "sub_test_456";
    private static final String TEST_CUST_ID = "cus_test_123";
    private static final String WEBHOOK_PATH = "/api/stripe/webhook";
    private static final String CANCEL_PATH = "/api/stripe/cancel";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantRepository tenantRepository;

    private LocalDate futureDate;
    private long futureEpoch;
    private LocalDate originalExpiry;

    private final Tenant baselineTenant = new Tenant();

    @BeforeEach
    void setUp() {
        futureDate = LocalDate.now().plusDays(30);
        futureEpoch = futureDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        originalExpiry = LocalDate.of(2026, 7, 1);

        baselineTenant.setTenantId(TEST_TENANT);
        baselineTenant.setName("Test Study Tenant");
        baselineTenant.setTenantGroup("study");
        baselineTenant.setEmail("test@example.com");
        baselineTenant.setStripeCustomerId(TEST_CUST_ID);
        baselineTenant.setStripeSubscriptionId(TEST_SUB_ID);
        baselineTenant.setSubscriptionPlan("monthly");
        baselineTenant.setSubscriptionExpiry(originalExpiry);
        baselineTenant.setCancelAtPeriodEnd(false);
        baselineTenant.setTrialEndsAt(null);
    }

    // ========================
    // Helpers
    // ========================

    /** Mock a Stripe Event. Event.getType() is final, so must use doReturn. */
    private Event fakeEvent(String eventType) {
        Event e = mock(Event.class);
        doReturn(eventType).when(e).getType();
        return e;
    }

    /** Mock a Stripe Subscription with periodEnd and metadata. */
    private Subscription fakeSubscription(Long periodEnd, Map<String, String> metadata) {
        Subscription s = mock(Subscription.class);
        doReturn(periodEnd).when(s).getCurrentPeriodEnd();
        doReturn(metadata != null ? metadata : Map.of()).when(s).getMetadata();
        return s;
    }

    private Tenant copyTenant() {
        Tenant t = new Tenant();
        t.setId(baselineTenant.getId());
        t.setTenantId(baselineTenant.getTenantId());
        t.setName(baselineTenant.getName());
        t.setTenantGroup(baselineTenant.getTenantGroup());
        t.setEmail(baselineTenant.getEmail());
        t.setStripeCustomerId(baselineTenant.getStripeCustomerId());
        t.setStripeSubscriptionId(baselineTenant.getStripeSubscriptionId());
        t.setSubscriptionPlan(baselineTenant.getSubscriptionPlan());
        t.setSubscriptionExpiry(baselineTenant.getSubscriptionExpiry());
        t.setCancelAtPeriodEnd(baselineTenant.getCancelAtPeriodEnd());
        t.setTrialEndsAt(baselineTenant.getTrialEndsAt());
        return t;
    }

    // ========================
    // Test 1: invoice.paid — updates expiry and clears cancelAtPeriodEnd
    // ========================

    @Test
    void invoicePaid_ShouldUpdateExpiryAndClearCancelFlag() throws Exception {
        Tenant t = copyTenant();
        t.setCancelAtPeriodEnd(true);
        when(tenantRepository.findByStripeSubscriptionId(TEST_SUB_ID)).thenReturn(Optional.of(t));

        String payload = """
                {"type":"invoice.paid","data":{"object":{"subscription":"%s","id":"in_001"}}}"""
                .formatted(TEST_SUB_ID);

        // Create mocks BEFORE mockStatic to avoid stubbing-inside-stubbing
        Event fake = fakeEvent("invoice.paid");
        Subscription fakeSub = fakeSubscription(futureEpoch, Map.of("tenant_id", TEST_TENANT));

        try (MockedStatic<Webhook> whMock = mockStatic(Webhook.class);
             MockedStatic<Subscription> subMock = mockStatic(Subscription.class)) {
            whMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(fake);
            subMock.when(() -> Subscription.retrieve(TEST_SUB_ID)).thenReturn(fakeSub);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)
                            .header("Stripe-Signature", "t=test,v1=dummy"))
                    .andExpect(status().isOk());
        }

        verify(tenantRepository).save(argThat(tenant ->
                futureDate.equals(tenant.getSubscriptionExpiry()) &&
                Boolean.FALSE.equals(tenant.getCancelAtPeriodEnd())
        ));
    }

    // ========================
    // Test 2: invoice.paid — fallback via metadata
    // ========================

    @Test
    void invoicePaid_ShouldUseFallbackMetadata_WhenSubscriptionIdNotInDB() throws Exception {
        when(tenantRepository.findByStripeSubscriptionId(TEST_SUB_ID)).thenReturn(Optional.empty());

        Tenant t = copyTenant();
        t.setStripeSubscriptionId("sub_wrong_id");
        t.setCancelAtPeriodEnd(true);
        when(tenantRepository.findByTenantId(TEST_TENANT)).thenReturn(Optional.of(t));

        String payload = """
                {"type":"invoice.paid","data":{"object":{"subscription":"%s","id":"in_002"}}}"""
                .formatted(TEST_SUB_ID);

        Event fake = fakeEvent("invoice.paid");
        Subscription fakeSub = fakeSubscription(futureEpoch, Map.of("tenant_id", TEST_TENANT));

        try (MockedStatic<Webhook> whMock = mockStatic(Webhook.class);
             MockedStatic<Subscription> subMock = mockStatic(Subscription.class)) {
            whMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(fake);
            subMock.when(() -> Subscription.retrieve(TEST_SUB_ID)).thenReturn(fakeSub);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)
                            .header("Stripe-Signature", "t=test,v1=dummy"))
                    .andExpect(status().isOk());
        }

        verify(tenantRepository).save(argThat(tenant ->
                futureDate.equals(tenant.getSubscriptionExpiry()) &&
                Boolean.FALSE.equals(tenant.getCancelAtPeriodEnd())
        ));
    }

    // ========================
    // Test 3: invoice.paid — no subscription in payload, should not throw
    // ========================

    @Test
    void invoicePaid_ShouldHandleMissingSubscriptionGracefully() throws Exception {
        String payload = """
                {"type":"invoice.paid","data":{"object":{"id":"in_003"}}}""";

        Event fake = fakeEvent("invoice.paid");

        try (MockedStatic<Webhook> whMock = mockStatic(Webhook.class)) {
            whMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(fake);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)
                            .header("Stripe-Signature", "t=test,v1=dummy"))
                    .andExpect(status().isOk());
        }

        verify(tenantRepository, never()).save(any());
    }

    // ========================
    // Test 4: invoice.payment_failed — logs only, no DB change
    // ========================

    @Test
    void invoicePaymentFailed_ShouldNotChangeDB() throws Exception {
        when(tenantRepository.findByStripeSubscriptionId(TEST_SUB_ID))
                .thenReturn(Optional.of(copyTenant()));

        String payload = """
                {"type":"invoice.payment_failed","data":{"object":{"subscription":"%s","id":"in_fail"}}}"""
                .formatted(TEST_SUB_ID);

        Event fake = fakeEvent("invoice.payment_failed");

        try (MockedStatic<Webhook> whMock = mockStatic(Webhook.class)) {
            whMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(fake);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)
                            .header("Stripe-Signature", "t=test,v1=dummy"))
                    .andExpect(status().isOk());
        }

        verify(tenantRepository, never()).save(any());
    }

    // ========================
    // Test 5: customer.subscription.updated — cancelAtPeriodEnd=true
    // ========================

    @Test
    void subscriptionUpdated_ShouldSetCancelFlagToTrue() throws Exception {
        Tenant t = copyTenant();
        t.setCancelAtPeriodEnd(false);
        when(tenantRepository.findByStripeSubscriptionId(TEST_SUB_ID)).thenReturn(Optional.of(t));

        String payload = """
                {"type":"customer.subscription.updated","data":{"object":{"id":"%s","cancel_at_period_end":true}}}"""
                .formatted(TEST_SUB_ID);

        Event fake = fakeEvent("customer.subscription.updated");

        try (MockedStatic<Webhook> whMock = mockStatic(Webhook.class)) {
            whMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(fake);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)
                            .header("Stripe-Signature", "t=test,v1=dummy"))
                    .andExpect(status().isOk());
        }

        verify(tenantRepository).save(argThat(tenant ->
                Boolean.TRUE.equals(tenant.getCancelAtPeriodEnd())
        ));
    }

    // ========================
    // Test 6: customer.subscription.updated — cancelAtPeriodEnd=false (reactivation)
    // ========================

    @Test
    void subscriptionUpdated_ShouldSetCancelFlagToFalse() throws Exception {
        Tenant t = copyTenant();
        t.setCancelAtPeriodEnd(true);
        when(tenantRepository.findByStripeSubscriptionId(TEST_SUB_ID)).thenReturn(Optional.of(t));

        String payload = """
                {"type":"customer.subscription.updated","data":{"object":{"id":"%s","cancel_at_period_end":false}}}"""
                .formatted(TEST_SUB_ID);

        Event fake = fakeEvent("customer.subscription.updated");

        try (MockedStatic<Webhook> whMock = mockStatic(Webhook.class)) {
            whMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(fake);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)
                            .header("Stripe-Signature", "t=test,v1=dummy"))
                    .andExpect(status().isOk());
        }

        verify(tenantRepository).save(argThat(tenant ->
                Boolean.FALSE.equals(tenant.getCancelAtPeriodEnd())
        ));
    }

    // ========================
    // Test 7: customer.subscription.deleted — clears all subscription fields
    // ========================

    @Test
    void subscriptionDeleted_ShouldClearAllSubscriptionFields() throws Exception {
        when(tenantRepository.findByStripeSubscriptionId(TEST_SUB_ID))
                .thenReturn(Optional.of(copyTenant()));

        String payload = """
                {"type":"customer.subscription.deleted","data":{"object":{"id":"%s"}}}"""
                .formatted(TEST_SUB_ID);

        Event fake = fakeEvent("customer.subscription.deleted");

        try (MockedStatic<Webhook> whMock = mockStatic(Webhook.class)) {
            whMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(fake);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)
                            .header("Stripe-Signature", "t=test,v1=dummy"))
                    .andExpect(status().isOk());
        }

        verify(tenantRepository).save(argThat(tenant ->
                tenant.getStripeSubscriptionId() == null &&
                tenant.getSubscriptionPlan() == null &&
                tenant.getSubscriptionExpiry() == null &&
                Boolean.FALSE.equals(tenant.getCancelAtPeriodEnd()) &&
                TEST_CUST_ID.equals(tenant.getStripeCustomerId())
        ));
    }

    // ========================
    // Test 8: checkout.session.completed — first-time subscription setup
    // ========================

    @Test
    void checkoutSessionCompleted_ShouldSetUpSubscriptionFields() throws Exception {
        Tenant t = copyTenant();
        t.setStripeCustomerId(null);
        t.setStripeSubscriptionId(null);
        t.setSubscriptionPlan(null);
        t.setSubscriptionExpiry(null);
        when(tenantRepository.findByTenantId(TEST_TENANT)).thenReturn(Optional.of(t));

        String newCustId = "cus_new_789";
        String newSubId = "sub_new_101112";

        String payload = """
                {"type":"checkout.session.completed","data":{"object":{"metadata":{"tenant_id":"%s","plan":"annual"},"customer":"%s","subscription":"%s"}}}"""
                .formatted(TEST_TENANT, newCustId, newSubId);

        Event fake = fakeEvent("checkout.session.completed");
        Subscription fakeSub = fakeSubscription(futureEpoch, null);

        try (MockedStatic<Webhook> whMock = mockStatic(Webhook.class);
             MockedStatic<Subscription> subMock = mockStatic(Subscription.class)) {
            whMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(fake);
            subMock.when(() -> Subscription.retrieve(newSubId)).thenReturn(fakeSub);

            mockMvc.perform(post(WEBHOOK_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)
                            .header("Stripe-Signature", "t=test,v1=dummy"))
                    .andExpect(status().isOk());
        }

        verify(tenantRepository).save(argThat(tenant ->
                newCustId.equals(tenant.getStripeCustomerId()) &&
                newSubId.equals(tenant.getStripeSubscriptionId()) &&
                "annual".equals(tenant.getSubscriptionPlan()) &&
                futureDate.equals(tenant.getSubscriptionExpiry())
        ));
    }

    // ========================
    // Test 9: Cancel endpoint — normal cancel
    // ========================

    @Test
    void cancelSubscription_ShouldSucceed_WithActiveSubscription() throws Exception {
        when(tenantRepository.findByTenantId(TEST_TENANT)).thenReturn(Optional.of(copyTenant()));

        Subscription fakeSub = fakeSubscription(futureEpoch, null);

        try (MockedStatic<Subscription> subMock = mockStatic(Subscription.class)) {
            subMock.when(() -> Subscription.retrieve(TEST_SUB_ID)).thenReturn(fakeSub);

            mockMvc.perform(post(CANCEL_PATH)
                            .header("X-Tenant-ID", TEST_TENANT)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true))
                    .andExpect(jsonPath("$.currentPeriodEnd").value(futureEpoch))
                    .andExpect(jsonPath("$.subscriptionExpiry").value(originalExpiry.toString()));
        }

        verify(tenantRepository).save(argThat(tenant ->
                Boolean.TRUE.equals(tenant.getCancelAtPeriodEnd())
        ));
    }

    // ========================
    // Test 10: Cancel endpoint — no active subscription
    // ========================

    @Test
    void cancelSubscription_ShouldReturn400_WhenNoSubscription() throws Exception {
        Tenant t = copyTenant();
        t.setStripeSubscriptionId(null);
        when(tenantRepository.findByTenantId(TEST_TENANT)).thenReturn(Optional.of(t));

        mockMvc.perform(post(CANCEL_PATH)
                        .header("X-Tenant-ID", TEST_TENANT)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No active subscription to cancel"));
    }

    // ========================
    // Test 11: Cancel endpoint — missing X-Tenant-ID header
    // ========================

    @Test
    void cancelSubscription_ShouldReturn400_WhenMissingTenantId() throws Exception {
        mockMvc.perform(post(CANCEL_PATH)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing X-Tenant-ID header"));
    }
}
