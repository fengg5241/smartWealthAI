package com.smartwealth.ai.service;

import com.smartwealth.ai.config.DemoProperties;
import com.smartwealth.ai.repository.AuditLogRepository;
import com.smartwealth.ai.domain.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);

    private final ChatClient chatClient;
    private final RagDocumentService ragDocumentService;
    private final AuditLogRepository auditLogRepository;
    private final DemoProperties properties;

    public RagChatService(ChatClient chatClient, RagDocumentService ragDocumentService,
                          AuditLogRepository auditLogRepository, DemoProperties properties) {
        this.chatClient = chatClient;
        this.ragDocumentService = ragDocumentService;
        this.auditLogRepository = auditLogRepository;
        this.properties = properties;
    }

    public record ChatResult(String answer, List<String> sources) {}

    public ChatResult ask(String tenantId, String userId, String question) {
        boolean useChinese = isChineseQuery(question);
        boolean isComparison = isComparisonQuery(question);
        String searchQuery;
        int topK = properties.getRag().getTopK();
        double similarityThreshold = properties.getRag().getSimilarityThreshold();

        if (isComparison) {
            searchQuery = question;
            topK = Math.max(topK, 20);
            similarityThreshold = 0.0;
        } else {
            searchQuery = rewriteQuery(question, useChinese);
        }

        List<Document> chunks = ragDocumentService.searchSimilarChunks(tenantId, searchQuery, topK, similarityThreshold);

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
                    .filter(name -> name != null)
                    .distinct()
                    .toList();
        }

        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // Save audit log
        try {
            AuditLog logEntry = new AuditLog();
            logEntry.setTenantId(tenantId);
            logEntry.setUserId(userId != null ? userId : "bot_user");
            logEntry.setQuestion(question);
            logEntry.setAnswer(answer);
            logEntry.setSources(String.join(",", sources));
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to save audit log for tenant={}", tenantId, e);
        }

        return new ChatResult(answer, sources);
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

    private boolean isComparisonQuery(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        for (String kw : COMPARISON_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private static final String[] COMPARISON_KEYWORDS = {
            "最高", "最低", "最大", "最小", "最多", "最少", "排名", "前十", "top",
            "highest", "lowest", "maximum", "minimum", "largest", "smallest",
            "most", "least", "top", "bottom", "ranking", "rank", "best", "worst"
    };

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
                    likely to appear in the indexed documents (tables, reports, policies, manuals). \
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
