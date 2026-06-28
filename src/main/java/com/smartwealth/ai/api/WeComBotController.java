package com.smartwealth.ai.api;

import com.smartwealth.ai.service.WeComBotService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;

@RestController
@RequestMapping("/api/wecom")
@ConditionalOnProperty(prefix = "wecom", name = "enabled", havingValue = "true")
public class WeComBotController {

    private static final Logger log = LoggerFactory.getLogger(WeComBotController.class);

    private final WeComBotService botService;

    public WeComBotController(WeComBotService botService) {
        this.botService = botService;
    }

    /**
     * WeCom server sends GET with signature/timestamp/nonce/echostr for URL verification.
     */
    @GetMapping("/callback")
    public ResponseEntity<String> verifyUrl(
            @RequestParam("msg_signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {

        if (!botService.isEnabled()) {
            return ResponseEntity.status(503).body("WeCom bot is disabled");
        }

        String result = botService.verifyUrl(signature, timestamp, nonce, echostr);
        if (result == null) {
            return ResponseEntity.badRequest().body("Verification failed");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * WeCom server sends POST with encrypted XML for messages.
     */
    @PostMapping("/callback")
    public ResponseEntity<String> handleMessage(
            @RequestParam("msg_signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            HttpServletRequest request) {

        if (!botService.isEnabled()) {
            return ResponseEntity.status(503).body("WeCom bot is disabled");
        }

        try {
            // Read raw XML body
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String body = sb.toString();

            log.debug("WeCom POST body (first 200 chars): {}", body.substring(0, Math.min(200, body.length())));

            String plainReply = botService.handleMessage(body, signature, timestamp, nonce);
            if (plainReply == null) {
                return ResponseEntity.ok("success");
            }

            String encrypted = botService.encryptReply(plainReply, timestamp, nonce);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_XML)
                    .body(encrypted);

        } catch (Exception e) {
            log.error("WeCom callback error", e);
            return ResponseEntity.ok("success"); // Always return 200 to WeCom
        }
    }
}
