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

        // Determine language and rewrite query for better document retrieval
        boolean useChinese = isChineseQuery(question);
        String searchQuery = rewriteQuery(question, useChinese);
        List<Document> chunks = ragDocumentService.searchSimilarChunks(tenantId, searchQuery);

        String prompt;
        List<String> sources;

        if (chunks.isEmpty()) {
            if (useChinese) {
                prompt = "用户问题：" + question
                        + "\n\n请根据你的知识回答用户问题。使用中文回答。";
            } else {
                prompt = "Question: " + question
                        + "\n\nAnswer the question based on your knowledge. Reply in English.";
            }
            sources = List.of();
        } else {
            String context = chunks.stream()
                    .map(doc -> "【Source: " + doc.getMetadata().get("fileName") + "】\n" + doc.getText())
                    .collect(Collectors.joining("\n\n"));

            if (useChinese) {
                prompt = """
                        基于以下参考资料回答用户问题。如果无法从参考资料中找到答案，请说"根据现有资料无法回答"。\
                        重要：当参考资料包含表格、且问题涉及最高/最低/最大/最小/排名时，你必须先逐行列出\
                        所有相关数据，再给出最终答案。不要中途停止扫描，务必检查完每一行。\
                        请使用中文回答。

                        【参考资料】
                        %s

                        【用户问题】
                        %s
                        """.formatted(context, question);
            } else {
                prompt = """
                        Answer the user's question based on the reference materials below. \
                        If the answer cannot be found in the reference materials, say \
                        "The answer cannot be found in the available documents." \
                        IMPORTANT: When the reference contains a table and the question asks \
                        for highest/lowest/max/min/top/bottom values, you MUST first list ALL \
                        rows with their values before selecting the answer. Do NOT stop reading \
                        halfway — check every row. \
                        Reply in English.

                        [Reference Materials]
                        %s

                        [Question]
                        %s
                        """.formatted(context, question);
            }

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

    private boolean isChineseQuery(String text) {
        if (text == null || text.isEmpty()) return true;
        int chineseChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            }
        }
        return chineseChars > text.length() / 4;
    }

    private String rewriteQuery(String originalQuestion, boolean useChinese) {
        String rewritePrompt;
        if (useChinese) {
            rewritePrompt = """
                    将以下用户问题改写为适合向量数据库搜索的关键词查询。\
                    将简短或模糊的问题扩展为可能出现在企业文档（政策、手册、SOP）中的具体术语。\
                    只输出改写后的查询，不要解释。

                    问题：%s
                    """.formatted(originalQuestion);
        } else {
            rewritePrompt = """
                    Rewrite the following user question into a search-friendly keyword query \
                    for a vector database. Expand short or vague questions with specific terms \
                    likely to appear in enterprise documents (policies, manuals, SOPs). \
                    Output only the rewritten query, no explanation.

                    Question: %s
                    """.formatted(originalQuestion);
        }
        String rewritten = chatClient.prompt()
                .user(rewritePrompt)
                .call()
                .content();
        if (rewritten == null || rewritten.isBlank()) {
            return originalQuestion;
        }
        return rewritten;
    }
}
