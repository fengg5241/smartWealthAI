package com.smartwealth.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class LearningAIService {

    private static final Logger log = LoggerFactory.getLogger(LearningAIService.class);
    private static final String VL_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    // Qwen3-Turbo via DashScope OpenAI-compatible endpoint
    private static final String CHAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore;

    public LearningAIService(VectorStore vectorStore) {
        this.restTemplate = new RestTemplate();
        this.apiKey = System.getenv("DASHSCOPE_API_KEY");
        this.objectMapper = new ObjectMapper();
        this.vectorStore = vectorStore;
    }

    // ==================== Page splitting ====================

    /**
     * Split a full page image into individual questions.
     * Returns a list of question descriptors: {number, text, bbox}.
     */
    public List<QuestionSplit> splitPageToQuestions(byte[] imageBytes) {
        String base64 = toDataUri(imageBytes, "image/jpeg");

        String prompt = """
            Analyze this page image. Identify each individual question/item on the page.
            For each question, capture the COMPLETE text — this means:
            - The question stem (the main question)
            - ALL answer choices/options (A, B, C, D or ① ② ③ ④ etc.)
            - Any diagrams, tables, or passages associated with the question
            Do NOT truncate or summarize. The full text of every option must be included in "text".
            Return: { "number": the question number (string),
              "text": the COMPLETE question text including all options,
              "bbox": { "x": left_pct, "y": top_pct, "w": width_pct, "h": height_pct } as approximate percentages (0-100) of the page dimensions }.
            Return a JSON array of these objects. Output ONLY the JSON array, no other text.""";

        String response = callVisionModel("qwen-vl-plus", base64, prompt, 4000, 0.2);

        try {
            List<QuestionSplit> results = new ArrayList<>();
            JsonNode arr = objectMapper.readTree(extractJsonArray(response));
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    String number = node.path("number").asText("");
                    String text = node.path("text").asText("");
                    JsonNode bbox = node.path("bbox");
                    int x = bbox.path("x").asInt(0);
                    int y = bbox.path("y").asInt(0);
                    int w = bbox.path("w").asInt(0);
                    int h = bbox.path("h").asInt(0);
                    results.add(new QuestionSplit(number, text, x, y, w, h));
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to parse page split response: {}", response, e);
            return List.of();
        }
    }

    // ==================== Mistake classification ====================

    /**
     * Classify a single mistake question from its OCR text and image.
     */
    public MistakeClassification classifyMistake(String ocrText, byte[] imageBytes) {
        String base64 = toDataUri(imageBytes, "image/jpeg");
        String prompt = """
            Analyze this student's incorrect answer question.
            Identify: subject (数学/语文/英语/科学/其他), questionType (e.g. 分数计算/应用题/图形题/语法填空/动词时态/阅读理解/错别字/病句/选择题/填空题),
            errorReason (one sentence in Chinese explaining why the student got it wrong),
            suggestedAnswer (the correct answer).
            Return a JSON object: {"subject":"...","questionType":"...","errorReason":"...","suggestedAnswer":"..."}.
            Output ONLY the JSON object.""";

        if (ocrText != null && !ocrText.isBlank()) {
            prompt = prompt.replace("Analyze this student's incorrect answer question.",
                    "Question text: " + truncate(ocrText, 600) + "\nAnalyze this student's incorrect answer question.");
        }

        String response = callVisionModel("qwen-vl-plus", base64, prompt, 500, 0.2);

        try {
            JsonNode j = objectMapper.readTree(extractJsonObject(response));
            return new MistakeClassification(
                    j.path("subject").asText("其他"),
                    j.path("questionType").asText(""),
                    j.path("errorReason").asText(""),
                    j.path("suggestedAnswer").asText(""));
        } catch (Exception e) {
            log.warn("Failed to parse mistake classification: {}", response, e);
            return new MistakeClassification("其他", "", "", "");
        }
    }

    // ==================== Phrase tagging ====================

    /**
     * Tag a good phrase with theme, emotion, usage type, and free tags.
     */
    public PhraseTags tagPhrase(String content) {
        String prompt = """
            Analyze this Chinese phrase/sentence from a student's collection of good writing:
            "%s"
            Return a JSON object: {"theme":"亲情/友情/自然/励志/季节/动物/生活/哲理/其他",
            "emotion":"温暖/豪迈/忧伤/欢快/宁静/激昂/幽默/其他",
            "usageType":"开头/结尾/描写人物/描写景物/修辞手法/抒情/议论/过渡/其他",
            "tags":["tag1","tag2"] (2-4 concise keywords)}.
            Output ONLY the JSON object.""".formatted(truncate(content, 500));

        String response = callTextModel("qwen-turbo", prompt, 300);

        try {
            JsonNode j = objectMapper.readTree(extractJsonObject(response));
            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = j.path("tags");
            if (tagsNode.isArray()) {
                for (JsonNode t : tagsNode) tags.add(t.asText());
            }
            return new PhraseTags(
                    j.path("theme").asText("其他"),
                    j.path("emotion").asText("其他"),
                    j.path("usageType").asText("其他"),
                    String.join(",", tags));
        } catch (Exception e) {
            log.warn("Failed to parse phrase tags: {}", response, e);
            return new PhraseTags("其他", "其他", "其他", "");
        }
    }

    // ==================== Handwriting removal ====================

    /**
     * Attempt to remove handwriting from the image.
     * Returns a text description of the clean version.
     * Note: true image removal may need additional image processing APIs.
     */
    public String removeHandwriting(byte[] imageBytes) {
        String base64 = toDataUri(imageBytes, "image/jpeg");
        String prompt = """
            Extract ONLY the printed question text from this image, ignoring ALL handwritten content
            (student answers, correction marks, scribbles). Output the clean question text only.""";

        return callVisionModel("qwen-vl-plus", base64, prompt, 2000, 0.1);
    }

    // ==================== Similar question generation ====================

    /**
     * Generate a similar question based on an existing mistake.
     */
    public SimilarQuestion generateSimilarQuestion(MistakeQuestionData data) {
        String prompt = """
            A student answered this question incorrectly: "%s" (subject: %s, type: %s, grade: %s).
            The error was: %s.
            Generate a NEW similar question that tests the same knowledge point but with different numbers/scenario.
            Return JSON: {"question":"the new question text","answer":"the correct answer","hint":"a brief hint"}.
            Output ONLY the JSON object."""
                .formatted(
                        truncate(data.content(), 400),
                        data.subject() != null ? data.subject() : "",
                        data.questionType() != null ? data.questionType() : "",
                        data.gradeLevel() != null ? data.gradeLevel() : "",
                        data.errorReason() != null ? data.errorReason() : "");

        String response = callTextModel("qwen-turbo", prompt, 600);

        try {
            JsonNode j = objectMapper.readTree(extractJsonObject(response));
            return new SimilarQuestion(
                    j.path("question").asText(""),
                    j.path("answer").asText(""),
                    j.path("hint").asText(""));
        } catch (Exception e) {
            log.warn("Failed to parse similar question: {}", response, e);
            return new SimilarQuestion("", "", "");
        }
    }

    // ==================== Text embedding ====================

    /**
     * Index mistake text into the vector store for semantic search.
     * Returns the vector document ID.
     */
    public String indexMistakeText(String tenantId, Long mistakeId, String searchableText) {
        String docId = UUID.nameUUIDFromBytes(
                (tenantId + "-mistake-" + mistakeId).getBytes(StandardCharsets.UTF_8)).toString();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", tenantId);
        metadata.put("mistakeId", mistakeId.toString());
        metadata.put("type", "mistake");
        vectorStore.add(List.of(new Document(docId, searchableText, metadata)));
        return docId;
    }

    /**
     * Index phrase text into the vector store for semantic search.
     */
    public String indexPhraseText(String tenantId, Long phraseId, String searchableText) {
        String docId = UUID.nameUUIDFromBytes(
                (tenantId + "-phrase-" + phraseId).getBytes(StandardCharsets.UTF_8)).toString();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", tenantId);
        metadata.put("phraseId", phraseId.toString());
        metadata.put("type", "phrase");
        vectorStore.add(List.of(new Document(docId, searchableText, metadata)));
        return docId;
    }

    /**
     * Remove a vector document by its ID.
     */
    public void removeVector(String vectorId) {
        if (vectorId != null) {
            try {
                vectorStore.delete(List.of(vectorId));
            } catch (Exception e) {
                log.warn("Failed to delete vector: {}", vectorId, e);
            }
        }
    }

    /**
     * Semantic search for mistakes by query text. Returns matching mistake IDs.
     */
    public List<Long> searchMistakes(String tenantId, String query, int limit) {
        var results = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(query)
                        .topK(limit)
                        .similarityThreshold(0.3)
                        .filterExpression("tenantId == '" + tenantId + "' AND type == 'mistake'")
                        .build());
        List<Long> ids = new ArrayList<>();
        for (var doc : results) {
            try {
                String idStr = doc.getMetadata().get("mistakeId").toString();
                ids.add(Long.parseLong(idStr));
            } catch (Exception ignored) {}
        }
        return ids;
    }

    /**
     * Semantic search for phrases by query text. Returns matching phrase IDs.
     */
    public List<Long> searchPhrases(String tenantId, String query, int limit) {
        var results = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(query)
                        .topK(limit)
                        .similarityThreshold(0.3)
                        .filterExpression("tenantId == '" + tenantId + "' AND type == 'phrase'")
                        .build());
        List<Long> ids = new ArrayList<>();
        for (var doc : results) {
            try {
                String idStr = doc.getMetadata().get("phraseId").toString();
                ids.add(Long.parseLong(idStr));
            } catch (Exception ignored) {}
        }
        return ids;
    }

    // ==================== Internals ====================

    private String callVisionModel(String model, String base64Image, String textPrompt, int maxTokens, double temp) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DASHSCOPE_API_KEY not set — vision call will fail");
            return "";
        }
        try {
            Map<String, Object> systemMsg = Map.of("role", "system", "content",
                    List.of(Map.of("text", "You are a helpful education assistant. Always respond with valid JSON only.")));
            Map<String, Object> userMsg = Map.of("role", "user", "content",
                    List.of(Map.of("image", base64Image),
                            Map.of("text", textPrompt)));

            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", Map.of("messages", List.of(systemMsg, userMsg)),
                    "parameters", Map.of("max_tokens", maxTokens, "temperature", temp));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = restTemplate.postForEntity(VL_URL, req, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                JsonNode choices = root.path("output").path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode content = choices.get(0).path("message").path("content");
                    if (content.isArray() && !content.isEmpty()) return content.get(0).path("text").asText("").trim();
                    return content.asText("").trim();
                }
            }
            log.warn("Vision model call failed: {}", resp.getStatusCode());
            return "";
        } catch (Exception e) {
            log.warn("Vision model call error", e);
            return "";
        }
    }

    private String callTextModel(String model, String prompt, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) return "";
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a helpful education assistant. Always respond with valid JSON only."),
                            Map.of("role", "user", "content", prompt)),
                    "temperature", 0.2,
                    "max_tokens", maxTokens);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = restTemplate.postForEntity(CHAT_URL, req, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    return choices.get(0).path("message").path("content").asText("").trim();
                }
            }
            return "";
        } catch (Exception e) {
            log.warn("Text model call error", e);
            return "";
        }
    }

    /**
     * Public entry point for raw text model calls (used by OCR phrase filtering).
     */
    public String callTextModelRaw(String model, String prompt, int maxTokens) {
        return callTextModel(model, prompt, maxTokens);
    }

    private static String extractJsonObject(String response) {
        if (response == null || response.isBlank()) return "{}";
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) return response.substring(start, end + 1);
        return response.trim();
    }

    private static String extractJsonArray(String response) {
        if (response == null || response.isBlank()) return "[]";
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start >= 0 && end > start) return response.substring(start, end + 1);
        return "[]";
    }

    private static String toDataUri(byte[] bytes, String mime) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    // ==================== Record types ====================

    public record QuestionSplit(String number, String text, int bboxX, int bboxY, int bboxW, int bboxH) {}
    public record MistakeClassification(String subject, String questionType, String errorReason, String suggestedAnswer) {}
    public record PhraseTags(String theme, String emotion, String usageType, String tags) {}
    public record SimilarQuestion(String question, String answer, String hint) {}
    public record MistakeQuestionData(String content, String subject, String questionType, String gradeLevel, String errorReason) {}
}
