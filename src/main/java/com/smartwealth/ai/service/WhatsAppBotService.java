package com.smartwealth.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwealth.ai.config.WhatsAppProperties;
import com.smartwealth.ai.domain.*;
import com.smartwealth.ai.repository.*;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@ConditionalOnProperty(prefix = "whatsapp", name = "enabled", havingValue = "true")
public class WhatsAppBotService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppBotService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WhatsAppProperties props;
    private final ImTenantMappingRepository mappingRepo;
    private final WaCustomerRepository customerRepo;
    private final WaConversationRepository conversationRepo;
    private final WaMessageRepository messageRepo;
    private final WaUsageLogRepository usageLogRepo;
    private final RagChatService ragChatService;
    private final HighIntentDetector highIntentDetector;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;
    private final SseEmitterPool ssePool;

    private static final List<String> ACTIVE_STATUSES = List.of("ai_active", "pending_human", "human_assigned");
    private static final BigDecimal COST_PER_MSG = new BigDecimal("0.005");

    public WhatsAppBotService(WhatsAppProperties props, ImTenantMappingRepository mappingRepo,
                              WaCustomerRepository customerRepo, WaConversationRepository conversationRepo,
                              WaMessageRepository messageRepo, WaUsageLogRepository usageLogRepo,
                              RagChatService ragChatService, HighIntentDetector highIntentDetector,
                              NotificationService notificationService, RateLimitService rateLimitService,
                              SseEmitterPool ssePool) {
        this.props = props;
        this.mappingRepo = mappingRepo;
        this.customerRepo = customerRepo;
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.usageLogRepo = usageLogRepo;
        this.ragChatService = ragChatService;
        this.highIntentDetector = highIntentDetector;
        this.notificationService = notificationService;
        this.rateLimitService = rateLimitService;
        this.ssePool = ssePool;
    }

    /**
     * Process incoming WhatsApp message. Called asynchronously from the webhook controller.
     */
    @Transactional
    public void handleIncoming(String from, String to, String body, String messageSid) {
        // 1. Idempotency check — prevent duplicate processing
        if (messageSid != null && !messageSid.isBlank()) {
            if (messageRepo.findByProviderMessageId(messageSid).isPresent()) {
                log.debug("Duplicate message ignored: sid={}", messageSid);
                return;
            }
        }

        // 2. Resolve tenant from the "To" phone number
        ImTenantMapping mapping = resolveMapping(to);
        if (mapping == null) {
            log.warn("No tenant mapping for WhatsApp number {}", to);
            return;
        }
        if (!mapping.isActive()) {
            log.info("Tenant mapping inactive for number {} tenant={}", to, mapping.getTenantId());
            return; // Silently ignore — already returned 200 in controller
        }
        String tenantId = mapping.getTenantId();

        // 3. Rate limit check (per-tenant TPS, before any DB/AI cost)
        String rateLimitErr = rateLimitService.checkRateLimit(tenantId, 5, 200, 5000);
        if (rateLimitErr != null) {
            log.warn("Rate limited tenant={}: {}", tenantId, rateLimitErr);
            sendWhatsAppMessage(tenantId, from, to, "We're experiencing high demand. Please try again shortly.");
            return;
        }

        // 4. Find or create customer
        WaCustomer customer = customerRepo.findByTenantIdAndWaPhone(tenantId, from)
                .orElseGet(() -> {
                    WaCustomer c = new WaCustomer(tenantId, from);
                    c.setSource("whatsapp");
                    return customerRepo.save(c);
                });

        // 5. Find active conversation
        WaConversation conv = conversationRepo
                .findFirstByTenantIdAndCustomerIdAndStatusInOrderByCreatedAtDesc(tenantId, customer.getId(), ACTIVE_STATUSES)
                .orElseGet(() -> conversationRepo.save(new WaConversation(tenantId, customer.getId())));

        // 6. Save inbound message
        WaMessage inMsg = new WaMessage(conv.getId(), "inbound", "customer", body);
        inMsg.setProviderMessageId(messageSid);
        messageRepo.save(inMsg);

        // 7. Update conversation tracking
        boolean is24hWindow = conv.getLastCustomerMessageAt() != null
                && Duration.between(conv.getLastCustomerMessageAt(), LocalDateTime.now()).toHours() > 24;
        conv.setLastCustomerMessageAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepo.save(conv);

        // 8. Log usage (inbound)
        logUsage(tenantId, conv.getId(), "inbound", messageSid);

        // 9. Detect high intent
        boolean highIntent = highIntentDetector.isHighIntent(body);

        // 10. Route based on conversation status
        if ("human_assigned".equals(conv.getStatus())) {
            conv.setUnreadCount(conv.getUnreadCount() + 1);
            conversationRepo.save(conv);
            pushToAgentPanel(conv, customer, inMsg, highIntent);
            return;
        }

        // 11. 24h window check — don't send session messages outside window
        if (is24hWindow) {
            log.warn("Outside 24h window: conv={} customer={}", conv.getId(), from);
            notificationService.notifyOutside24h(conv, from, body);
            pushAgentEvent(conv, customer, "outside_24h",
                    Map.of("customerPhone", from, "message", body,
                            "error", "OUTSIDE_24H_WINDOW", "suggestion", "USE_TEMPLATE"));
            return;
        }

        // 12. AI reply
        RagChatService.ChatResult result = ragChatService.ask(tenantId, "wa:" + from, body);
        String reply = buildReply(result);

        // 13. Check if AI is uncertain → transition to pending_human
        if (isUncertainAnswer(result)) {
            if ("ai_active".equals(conv.getStatus())) {
                conv.setStatus("pending_human");
                conversationRepo.save(conv);
            }
            pushToAgentPanel(conv, customer, inMsg, highIntent);
            notificationService.notifyAiFailed(conv, from, body);
        }

        // 14. Send via Twilio
        sendWhatsAppMessage(tenantId, from, to, reply);

        // 15. Save outbound AI message
        WaMessage outMsg = new WaMessage(conv.getId(), "outbound", "ai", reply);
        if (!result.sources().isEmpty()) {
            try {
                outMsg.setMetadata(objectMapper.writeValueAsString(Map.of("sources", result.sources())));
            } catch (Exception e) {
                log.warn("Failed to serialize metadata", e);
            }
        }
        messageRepo.save(outMsg);

        // 16. Log usage (outbound)
        logUsage(tenantId, conv.getId(), "outbound", null);

        // 17. Push to agent panel
        pushToAgentPanel(conv, customer, outMsg, false);

        // 18. Notifications
        if (highIntent) {
            notificationService.notifyHighIntent(conv, from, body);
        }

    }

    /** Send WhatsApp message via Twilio. Resolves tenant-specific credentials from bot_token JSON. */
    public void sendWhatsAppMessage(String tenantId, String to, String from, String body) {
        try {
            // Normalize phone numbers — strip any existing "whatsapp:" prefix
            String toPhone = to.replace("whatsapp:", "");
            String fromPhone = from.replace("whatsapp:", "");
            String accountSid = props.getAccountSid();
            String authToken = props.getAuthToken();

            // Check if tenant has its own provider credentials in bot_token
            ImTenantMapping mapping = mappingRepo.findByPlatformAndPlatformTeamId("whatsapp",
                    from.replace("whatsapp:", "")).orElse(null);
            if (mapping != null && mapping.getBotToken() != null && !mapping.getBotToken().isBlank()) {
                try {
                    Map<String, Object> creds = objectMapper.readValue(mapping.getBotToken(), Map.class);
                    if (creds.containsKey("accountSid")) accountSid = (String) creds.get("accountSid");
                    if (creds.containsKey("authToken")) authToken = (String) creds.get("authToken");
                } catch (Exception e) {
                    log.warn("Failed to parse bot_token JSON for tenant={}, using default", tenantId);
                }
            }

            Twilio.init(accountSid, authToken);
            Message message = Message.creator(
                    new PhoneNumber("whatsapp:" + toPhone),
                    new PhoneNumber("whatsapp:" + fromPhone),
                    body).create();
            log.info("Sent WhatsApp message sid={} to={}", message.getSid(), to);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to={}", to, e);
            throw new RuntimeException("Failed to send WhatsApp message", e);
        }
    }

    /** Agent sends a manual reply from the panel. */
    public WaMessage sendAgentReply(Long conversationId, String tenantId, String agentName, String content) {
        WaConversation conv = conversationRepo.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // Only assigned agent can reply
        if (!"human_assigned".equals(conv.getStatus())) {
            throw new RuntimeException("Conversation is not in human_assigned status");
        }
        if (conv.getAssignedAgent() != null && !conv.getAssignedAgent().equals(agentName)) {
            throw new RuntimeException("Only " + conv.getAssignedAgent() + " can reply to this conversation");
        }

        // 24h check for agent replies too
        if (conv.getLastCustomerMessageAt() != null
                && Duration.between(conv.getLastCustomerMessageAt(), LocalDateTime.now()).toHours() > 24) {
            throw new RuntimeException("OUTSIDE_24H_WINDOW: Cannot send session messages after 24h. Use a template.");
        }

        WaCustomer customer = customerRepo.findById(conv.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        String twilioFrom = resolveTwilioNumber(tenantId);
        sendWhatsAppMessage(tenantId, customer.getWaPhone(), twilioFrom, content);

        WaMessage msg = new WaMessage(conv.getId(), "outbound", "agent", content);
        msg = messageRepo.save(msg);
        conv.setUpdatedAt(LocalDateTime.now());
        conv.setUnreadCount(0);
        conversationRepo.save(conv);

        logUsage(tenantId, conv.getId(), "outbound", null);
        pushToAgentPanel(conv, customer, msg, false);

        return msg;
    }

    /** Take over a conversation. Returns true on success, false if someone else already took it. */
    @Transactional
    public boolean takeOver(Long conversationId, String tenantId, String agentName) {
        int updated = conversationRepo.takeOver(conversationId, tenantId, agentName);
        if (updated > 0) {
            // Push takeover event to all agents
            var conv = conversationRepo.findById(conversationId).orElse(null);
            if (conv != null) {
                pushAgentEvent(conv, null, "take_over",
                        Map.of("status", "human_assigned", "assignedAgent", agentName));
            }
        }
        return updated > 0;
    }

    /** Release a conversation back to AI. */
    @Transactional
    public boolean release(Long conversationId, String tenantId, String agentName) {
        return conversationRepo.release(conversationId, tenantId, agentName) > 0;
    }

    @Transactional
    public void closeConversation(Long conversationId, String tenantId) {
        WaConversation conv = conversationRepo.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        conv.setStatus("closed");
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepo.save(conv);
    }

    // -- Private helpers --

    private ImTenantMapping resolveMapping(String twilioPhoneNumber) {
        String phone = twilioPhoneNumber.replace("whatsapp:", "");
        return mappingRepo.findByPlatformAndPlatformTeamId("whatsapp", phone).orElse(null);
    }

    private String resolveTwilioNumber(String tenantId) {
        return props.getPhoneNumber();
    }

    private void logUsage(String tenantId, Long convId, String direction, String twilioSid) {
        try {
            WaUsageLog log = new WaUsageLog(tenantId, convId, direction);
            log.setTwilioMessageSid(twilioSid);
            log.setCost(COST_PER_MSG);
            usageLogRepo.save(log);
        } catch (Exception e) {
            log.warn("Failed to log usage for tenant={}", tenantId, e);
        }
    }

    private void pushToAgentPanel(WaConversation conv, WaCustomer customer, WaMessage msg, boolean highIntent) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "new_message");
        event.put("conversationId", conv.getId());
        event.put("customerId", customer != null ? customer.getId() : null);
        event.put("customerPhone", customer != null ? customer.getWaPhone() : null);
        event.put("customerName", customer != null ? customer.getDisplayName() : null);
        event.put("messageId", msg.getId());
        event.put("content", msg.getContent());
        event.put("direction", msg.getDirection());
        event.put("senderType", msg.getSenderType());
        event.put("conversationStatus", conv.getStatus());
        event.put("unreadCount", conv.getUnreadCount());
        event.put("highIntent", highIntent);
        event.put("timestamp", msg.getCreatedAt().toString());
        ssePool.push(conv.getTenantId(), event);
    }

    private void pushAgentEvent(WaConversation conv, WaCustomer customer, String type, Map<String, Object> extra) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("conversationId", conv.getId());
        event.put("customerPhone", customer != null ? customer.getWaPhone() : null);
        event.put("conversationStatus", conv.getStatus());
        event.putAll(extra);
        ssePool.push(conv.getTenantId(), event);
    }

    private String buildReply(RagChatService.ChatResult result) {
        return result.answer();
    }

    private boolean isUncertainAnswer(RagChatService.ChatResult result) {
        if (result.sources().isEmpty()) return true;
        String answer = result.answer();
        if (answer == null || answer.isBlank()) return true;
        String lower = answer.toLowerCase();
        return lower.contains("根据现有资料无法回答")
                || lower.contains("cannot be found")
                || lower.contains("i don't know")
                || lower.contains("i'm not sure");
    }
}
