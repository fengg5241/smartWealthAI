package com.smartwealth.ai.api;

import com.smartwealth.ai.service.RagChatService;
import com.smartwealth.ai.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RagChatService ragChatService;

    public ChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    @PostMapping("/completions")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Tenant-ID header"));
        }

        String question = request.get("message");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
        }

        RagChatService.ChatResult result = ragChatService.ask(tenantId, "demo_user", question);

        return ResponseEntity.ok(Map.of(
                "answer", result.answer(),
                "sources", result.sources()
        ));
    }
}
