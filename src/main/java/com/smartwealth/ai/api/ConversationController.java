package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.WaConversation;
import com.smartwealth.ai.domain.WaCustomer;
import com.smartwealth.ai.domain.WaMessage;
import com.smartwealth.ai.repository.WaConversationRepository;
import com.smartwealth.ai.repository.WaCustomerRepository;
import com.smartwealth.ai.repository.WaMessageRepository;
import com.smartwealth.ai.repository.WaUsageLogRepository;
import com.smartwealth.ai.service.SseEmitterPool;
import com.smartwealth.ai.service.WhatsAppBotService;
import com.smartwealth.ai.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final WaConversationRepository conversationRepo;
    private final WaCustomerRepository customerRepo;
    private final WaMessageRepository messageRepo;
    private final WaUsageLogRepository usageLogRepo;
    private final WhatsAppBotService botService;
    private final SseEmitterPool emitterPool;

    public ConversationController(WaConversationRepository conversationRepo,
                                  WaCustomerRepository customerRepo,
                                  WaMessageRepository messageRepo,
                                  WaUsageLogRepository usageLogRepo,
                                  WhatsAppBotService botService,
                                  SseEmitterPool emitterPool) {
        this.conversationRepo = conversationRepo;
        this.customerRepo = customerRepo;
        this.messageRepo = messageRepo;
        this.usageLogRepo = usageLogRepo;
        this.botService = botService;
        this.emitterPool = emitterPool;
    }

    /** SSE stream — agent panel subscribes to real-time updates. Supports Last-Event-ID reconnect. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            SseEmitter bad = new SseEmitter(0L);
            bad.completeWithError(new IllegalStateException("Missing X-Tenant-ID"));
            return bad;
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        emitterPool.add(tenantId, emitter);

        emitter.onCompletion(() -> emitterPool.remove(tenantId, emitter));
        emitter.onTimeout(() -> emitterPool.remove(tenantId, emitter));
        emitter.onError(e -> emitterPool.remove(tenantId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            emitterPool.remove(tenantId, emitter);
        }

        return emitter;
    }

    /** List all non-closed conversations for the current tenant. */
    @GetMapping
    public ResponseEntity<?> listConversations() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID");

        List<Map<String, Object>> conversations = conversationRepo
                .findByTenantIdAndStatusNotOrderByUpdatedAtDesc(tenantId, "closed")
                .stream()
                .map(conv -> {
                    WaCustomer customer = customerRepo.findById(conv.getCustomerId()).orElse(null);
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", conv.getId());
                    dto.put("status", conv.getStatus());
                    dto.put("assignedAgent", conv.getAssignedAgent());
                    dto.put("unreadCount", conv.getUnreadCount());
                    dto.put("updatedAt", conv.getUpdatedAt());
                    if (customer != null) {
                        dto.put("customerId", customer.getId());
                        dto.put("customerPhone", customer.getWaPhone());
                        dto.put("customerName", customer.getDisplayName());
                        dto.put("tags", customer.getTags());
                        dto.put("budget", customer.getBudget());
                        dto.put("requirement", customer.getRequirement());
                    }
                    List<WaMessage> messages = messageRepo.findByConversationIdOrderByCreatedAtAsc(conv.getId());
                    if (!messages.isEmpty()) {
                        WaMessage last = messages.get(messages.size() - 1);
                        dto.put("lastMessage", last.getContent().length() > 80
                                ? last.getContent().substring(0, 80) + "..." : last.getContent());
                        dto.put("lastMessageTime", last.getCreatedAt());
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(conversations);
    }

    /** Get message history for a conversation. */
    @GetMapping("/{id}/messages")
    public ResponseEntity<?> getMessages(@PathVariable Long id,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID");

        WaConversation conv = conversationRepo.findByIdAndTenantId(id, tenantId).orElse(null);
        if (conv == null) return ResponseEntity.notFound().build();

        var msgs = messageRepo.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                id, LocalDateTime.now().plusDays(1), PageRequest.of(page, size));

        List<Map<String, Object>> result = msgs.getContent().stream()
                .map(m -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", m.getId());
                    dto.put("direction", m.getDirection());
                    dto.put("senderType", m.getSenderType());
                    dto.put("messageType", m.getMessageType());
                    dto.put("content", m.getContent());
                    dto.put("metadata", m.getMetadata());
                    dto.put("createdAt", m.getCreatedAt());
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "messages", result,
                "totalPages", msgs.getTotalPages(),
                "totalElements", msgs.getTotalElements()
        ));
    }

    /** Agent sends a manual reply. */
    @PostMapping("/{id}/reply")
    public ResponseEntity<?> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        String content = body.get("content");
        String agentName = body.getOrDefault("agentName", "agent");
        if (tenantId == null) return bad("Missing X-Tenant-ID");
        if (content == null || content.isBlank()) return bad("content required");

        try {
            WaMessage msg = botService.sendAgentReply(id, tenantId, agentName, content);
            return ResponseEntity.ok(Map.of(
                    "id", msg.getId(), "content", msg.getContent(),
                    "senderType", msg.getSenderType(), "createdAt", msg.getCreatedAt().toString()
            ));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("OUTSIDE_24H_WINDOW")) {
                return ResponseEntity.status(400).body(Map.of(
                        "error", "OUTSIDE_24H_WINDOW", "suggestion", "USE_TEMPLATE"
                ));
            }
            if (e.getMessage() != null && e.getMessage().contains("can reply")) {
                return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
            }
            throw e;
        }
    }

    /** Take over a conversation (optimistic lock). */
    @PutMapping("/{id}/take-over")
    public ResponseEntity<?> takeOver(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        String agentName = body.getOrDefault("agentName", "agent");
        if (tenantId == null) return bad("Missing X-Tenant-ID");

        boolean ok = botService.takeOver(id, tenantId, agentName);
        if (!ok) return ResponseEntity.status(409).body(Map.of(
                "error", "CONFLICT", "message", "This conversation has already been taken over by another agent."
        ));

        return ResponseEntity.ok(Map.of("status", "human_assigned", "assignedAgent", agentName));
    }

    /** Release a conversation back to AI. */
    @PutMapping("/{id}/release")
    public ResponseEntity<?> release(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        String agentName = body.getOrDefault("agentName", "agent");
        if (tenantId == null) return bad("Missing X-Tenant-ID");

        boolean ok = botService.release(id, tenantId, agentName);
        if (!ok) return ResponseEntity.status(409).body(Map.of(
                "error", "CONFLICT", "message", "Cannot release — you are not the assigned agent or status has changed."
        ));

        return ResponseEntity.ok(Map.of("status", "ai_active"));
    }

    /** Close a conversation. */
    @PutMapping("/{id}/close")
    public ResponseEntity<?> close(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID");
        botService.closeConversation(id, tenantId);
        return ResponseEntity.ok(Map.of("status", "closed"));
    }

    /** Update customer profile. */
    @PutMapping("/{id}/customer")
    public ResponseEntity<?> updateCustomer(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID");

        WaConversation conv = conversationRepo.findByIdAndTenantId(id, tenantId).orElse(null);
        if (conv == null) return ResponseEntity.notFound().build();

        WaCustomer customer = customerRepo.findById(conv.getCustomerId()).orElse(null);
        if (customer == null) return ResponseEntity.notFound().build();

        if (body.containsKey("tags")) customer.setTags(body.get("tags"));
        if (body.containsKey("budget")) customer.setBudget(body.get("budget"));
        if (body.containsKey("requirement")) customer.setRequirement(body.get("requirement"));
        if (body.containsKey("displayName")) customer.setDisplayName(body.get("displayName"));
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepo.save(customer);

        return ResponseEntity.ok(Map.of("message", "Customer updated"));
    }

    /** Get current tenant usage. */
    @GetMapping("/usage")
    public ResponseEntity<?> getUsage() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID");

        var startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        int sent = usageLogRepo.countOutboundSince(tenantId, startOfMonth);
        int total = usageLogRepo.countByTenantIdAndCreatedAtAfter(tenantId, startOfMonth);

        return ResponseEntity.ok(Map.of(
                "sent", sent,
                "total", total,
                "month", LocalDate.now().getMonth().toString()
        ));
    }

    private static ResponseEntity<Map<String, String>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }
}
