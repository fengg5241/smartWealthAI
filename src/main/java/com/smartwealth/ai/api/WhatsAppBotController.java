package com.smartwealth.ai.api;

import com.smartwealth.ai.config.WhatsAppProperties;
import com.smartwealth.ai.service.WhatsAppBotService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/whatsapp")
@ConditionalOnProperty(prefix = "whatsapp", name = "enabled", havingValue = "true")
public class WhatsAppBotController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppBotController.class);

    private final WhatsAppProperties props;
    private final WhatsAppBotService botService;

    public WhatsAppBotController(WhatsAppProperties props, WhatsAppBotService botService) {
        this.props = props;
        this.botService = botService;
    }

    /**
     * Twilio webhook for incoming WhatsApp messages.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(HttpServletRequest request) {
        try {
            String body = request.getReader().lines().collect(Collectors.joining("&"));
            Map<String, String> params = parseFormEncoded(body);

            // Verify Twilio signature
            String twilioSignature = request.getHeader("X-Twilio-Signature");
            String fullUrl = request.getRequestURL().toString();
            Map<String, String> sortedParams = new TreeMap<>(params);
            if (!verifySignature(fullUrl, sortedParams, twilioSignature)) {
                log.warn("Invalid Twilio signature from {}", params.get("From"));
                return ResponseEntity.ok().build();
            }

            String from = params.get("From");
            String to = params.get("To");
            String messageBody = params.get("Body");
            String messageSid = params.get("MessageSid");
            String numMedia = params.get("NumMedia");

            if (from == null || to == null) {
                return ResponseEntity.ok().build();
            }

            // Handle media — V1: send a polite fallback
            if (numMedia != null && Integer.parseInt(numMedia) > 0) {
                log.info("Received media from {}: type={}", from, params.get("MediaContentType0"));
                botService.sendWhatsAppMessage(null,
                        from.replace("whatsapp:", ""),
                        to.replace("whatsapp:", ""),
                        "Received your file, thank you! An agent will review it and get back to you shortly.");
                return ResponseEntity.ok().build();
            }

            // Process text message asynchronously
            if (messageBody != null && !messageBody.isBlank()) {
                final String f = from;
                final String t = to;
                final String msg = messageBody;
                final String sid = messageSid;
                new Thread(() -> botService.handleIncoming(f, t, msg, sid)).start();
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("WhatsApp webhook error", e);
            return ResponseEntity.ok().build();
        }
    }

    /**
     * Send a message from the agent panel. Requires X-Tenant-ID header.
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> body) {
        try {
            Long conversationId = Long.valueOf(body.get("conversationId").toString());
            String content = (String) body.get("content");
            String agentName = (String) body.getOrDefault("agentName", "agent");
            String tenantId = com.smartwealth.ai.tenant.TenantContext.getCurrentTenantId();

            if (tenantId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
            }

            var msg = botService.sendAgentReply(conversationId, tenantId, agentName, content);
            return ResponseEntity.ok(Map.of(
                    "id", msg.getId(),
                    "content", msg.getContent(),
                    "senderType", msg.getSenderType(),
                    "createdAt", msg.getCreatedAt().toString()
            ));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("OUTSIDE_24H_WINDOW")) {
                return ResponseEntity.status(400).body(Map.of(
                        "error", "OUTSIDE_24H_WINDOW",
                        "suggestion", "USE_TEMPLATE"
                ));
            }
            log.error("Failed to send WhatsApp message", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // --- Signature verification ---

    private boolean verifySignature(String url, Map<String, String> params, String signature) {
        if (props.getAuthToken() == null || props.getAuthToken().isBlank()) {
            return true; // Dev mode
        }
        try {
            StringBuilder content = new StringBuilder(url);
            for (Map.Entry<String, String> entry : params.entrySet()) {
                content.append(entry.getKey()).append(entry.getValue());
            }

            Mac hmac = Mac.getInstance("HmacSHA1");
            hmac.init(new SecretKeySpec(props.getAuthToken().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] hash = hmac.doFinal(content.toString().getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);

            return computed.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error", e);
            return false;
        }
    }

    private static Map<String, String> parseFormEncoded(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isBlank()) return map;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }
}
