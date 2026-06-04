package com.smartwealth.ai.api;

import com.smartwealth.ai.config.DemoProperties;
import com.smartwealth.ai.domain.AuditLog;
import com.smartwealth.ai.repository.AuditLogRepository;
import com.smartwealth.ai.service.RagDocumentService;
import com.smartwealth.ai.tenant.TenantContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final RagDocumentService ragDocumentService;
    private final AuditLogRepository auditLogRepository;
    private final DemoProperties properties;

    public ChatController(ChatClient chatClient, RagDocumentService ragDocumentService,
                           AuditLogRepository auditLogRepository, DemoProperties properties) {
        this.chatClient = chatClient;
        this.ragDocumentService = ragDocumentService;
        this.auditLogRepository = auditLogRepository;
        this.properties = properties;
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

        // Search relevant document chunks for this tenant
        List<Document> chunks = ragDocumentService.searchSimilarChunks(tenantId, question);

        String prompt;
        List<String> sources;

        if (chunks.isEmpty()) {
            prompt = "用户问题：" + question
                    + "\n\n请根据你的知识回答用户问题。如果你的知识与财富管理、税务、保险或投资无关，请直接回答。";
            sources = List.of();
        } else {
            String context = chunks.stream()
                    .map(doc -> "【来源：" + doc.getMetadata().get("fileName") + "】\n" + doc.getText())
                    .collect(Collectors.joining("\n\n"));

            prompt = """
                    基于以下参考资料回答用户问题。如果无法从参考资料中找到答案，请说"根据现有资料无法回答"。

                    【参考资料】
                    %s

                    【用户问题】
                    %s
                    """.formatted(context, question);

            sources = chunks.stream()
                    .map(doc -> (String) doc.getMetadata().get("fileName"))
                    .distinct()
                    .toList();
        }

        // Generate answer
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // Save audit log
        AuditLog log = new AuditLog();
        log.setUserId("demo_user");
        log.setQuestion(question);
        log.setAnswer(answer);
        log.setSources(String.join(",", sources));
        auditLogRepository.save(log);

        return ResponseEntity.ok(Map.of(
                "answer", answer,
                "sources", sources
        ));
    }
}
