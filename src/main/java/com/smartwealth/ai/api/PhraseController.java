package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.GoodPhrase;
import com.smartwealth.ai.service.GoodPhraseService;
import com.smartwealth.ai.service.LearningAIService;
import com.smartwealth.ai.service.OcrService;
import com.smartwealth.ai.service.ReviewService;
import com.smartwealth.ai.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

@RestController
@RequestMapping("/api/phrases")
public class PhraseController {

    private static final Logger log = LoggerFactory.getLogger(PhraseController.class);

    private final GoodPhraseService phraseService;
    private final OcrService ocrService;
    private final LearningAIService learningAI;
    private final ReviewService reviewService;
    private final com.aliyun.oss.OSS ossClient;
    private final com.smartwealth.ai.config.OssConfig.OssProperties ossProperties;

    public PhraseController(GoodPhraseService phraseService,
                            OcrService ocrService,
                            LearningAIService learningAI,
                            ReviewService reviewService,
                            com.aliyun.oss.OSS ossClient,
                            com.smartwealth.ai.config.OssConfig.OssProperties ossProperties) {
        this.phraseService = phraseService;
        this.ocrService = ocrService;
        this.learningAI = learningAI;
        this.reviewService = reviewService;
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam(value = "content", required = false, defaultValue = "") String content,
            @RequestParam(value = "source", required = false, defaultValue = "") String source,
            @RequestParam(value = "theme", required = false, defaultValue = "") String theme,
            @RequestParam(value = "emotion", required = false, defaultValue = "") String emotion,
            @RequestParam(value = "usageType", required = false, defaultValue = "") String usageType,
            @RequestParam(value = "tags", required = false, defaultValue = "") String tags,
            @RequestParam(value = "masteryLevel", required = false, defaultValue = "不熟悉") String masteryLevel,
            @RequestParam(value = "language", required = false, defaultValue = "") String language,
            @RequestParam(value = "entryMethod", required = false, defaultValue = "") String entryMethod,
            @RequestParam(value = "notebookId", required = false) Long notebookId,
            @RequestParam(value = "image", required = false) MultipartFile file) {

        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        if (content.isBlank()) return bad("content is required");

        try {
            GoodPhraseService.PhraseInput input = new GoodPhraseService.PhraseInput();
            input.content = content.trim();
            input.source = blankToNull(source);
            input.theme = blankToNull(theme);
            input.emotion = blankToNull(emotion);
            input.usageType = blankToNull(usageType);
            input.tags = blankToNull(tags);
            input.masteryLevel = masteryLevel;
            input.language = blankToNull(language);
            input.notebookId = notebookId;

            byte[] imageBytes = file != null ? file.getBytes() : null;
            String contentType = file != null ? file.getContentType() : null;
            // entryMethod hint: if user explicitly tagged as "photo" but no file, still treat as text
            if (imageBytes != null && blankToNull(entryMethod) == null) {
                // image provided without explicit entryMethod — will be auto-set in service
            }

            GoodPhrase phrase = phraseService.create(tenantId, input, imageBytes, contentType);
            reviewService.schedulePhraseForReview(tenantId, phrase.getId());
            return ResponseEntity.ok(toMap(phrase));
        } catch (Exception e) {
            log.error("Create phrase failed", e);
            return bad(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "theme", required = false) String theme,
            @RequestParam(value = "emotion", required = false) String emotion,
            @RequestParam(value = "masteryLevel", required = false) String masteryLevel,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "notebookId", required = false) Long notebookId) {

        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        List<GoodPhrase> phrases = phraseService.list(tenantId,
                blankToNull(theme), blankToNull(emotion),
                blankToNull(masteryLevel), blankToNull(language),
                blankToNull(tags), notebookId);
        return ResponseEntity.ok(Map.of(
                "phrases", phrases.stream().map(this::toMap).toList(),
                "themes", phraseService.getThemes(tenantId),
                "emotions", phraseService.getEmotions(tenantId),
                "tags", phraseService.getTags(tenantId),
                "languages", phraseService.getLanguages(tenantId),
                "count", phrases.size()));
    }

    @GetMapping("/tags")
    public ResponseEntity<?> getTags() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");
        return ResponseEntity.ok(Map.of("tags", phraseService.getTags(tenantId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        return phraseService.get(tenantId, id)
                .map(p -> ResponseEntity.ok(toMap(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return ResponseEntity.badRequest().build();

        return phraseService.get(tenantId, id)
                .filter(p -> p.getImagePath() != null)
                .map(p -> {
                    try (InputStream is = ossClient.getObject(ossProperties.getBucket(), p.getImagePath()).getObjectContent();
                         ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                        byte[] data = new byte[8192];
                        int n;
                        while ((n = is.read(data)) != -1) buf.write(data, 0, n);
                        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(buf.toByteArray());
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError().<byte[]>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            GoodPhraseService.PhraseInput input = new GoodPhraseService.PhraseInput();
            input.content = stringField(body, "content");
            input.source = stringField(body, "source");
            input.theme = stringField(body, "theme");
            input.emotion = stringField(body, "emotion");
            input.usageType = stringField(body, "usageType");
            input.tags = stringField(body, "tags");
            input.masteryLevel = stringField(body, "masteryLevel");
            input.language = stringField(body, "language");
            if (body.get("notebookId") instanceof Number n) input.notebookId = n.longValue();

            GoodPhrase updated = phraseService.update(tenantId, id, input);
            return ResponseEntity.ok(toMap(updated));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            phraseService.delete(tenantId, id);
            return ResponseEntity.ok(Map.of("message", "Deleted"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/ocr")
    public ResponseEntity<Map<String, Object>> ocrPhrases(
            @RequestParam("image") MultipartFile file,
            @RequestParam(value = "standard", required = false, defaultValue = "PSLE") String standard) {

        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");
        if (file == null || file.isEmpty()) return bad("Image is required");

        try {
            byte[] imageBytes = file.getBytes();
            String ocrText = ocrService.ocrImage(imageBytes);

            if (ocrText == null || ocrText.isBlank()) {
                return ResponseEntity.ok(Map.of("candidates", List.of()));
            }

            // Build grading-standard-specific filter prompt
            String gradingCriteria = switch (standard.toUpperCase()) {
                case "O_LEVEL", "OLEVEL" -> """
                    O Level (Secondary 4): Sophisticated vocabulary, effective metaphors,
                    nuanced emotional language, rhetorical devices, mature sentence
                    structure. A "good phrase" demonstrates stylistic control.""";
                case "A_LEVEL", "ALEVEL" -> """
                    A Level (JC/MI Year 2): Advanced literary techniques, precise diction,
                    complex rhetorical strategies, original voice. A "good phrase" shows
                    mastery of language for effect.""";
                default -> """
                    PSLE (Primary 6): Vivid adjectives, similes, emotional expressions,
                    appropriate idioms, varied sentence starters. A "good phrase" at this
                    level shows effort beyond basic description.""";
            };

            String prompt = """
                You are a writing evaluator. Given the extracted text from a student's notebook,
                identify and extract sentences/phrases that qualify as "good phrases"
                according to the grading criteria below.

                Grading Standard:
                %s

                Extracted text:
                %s

                Return ONLY a JSON array of the qualifying sentences/phrases.
                Each element should be the full sentence/phrase text, nothing else.
                If none qualify, return an empty array [].""".formatted(gradingCriteria, ocrText);

            String response = learningAI.callTextModelRaw("qwen-turbo", prompt, 1000);

            // Parse JSON array from response
            List<String> candidates = new ArrayList<>();
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                int start = response.indexOf('[');
                int end = response.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    String jsonArr = response.substring(start, end + 1);
                    com.fasterxml.jackson.databind.JsonNode arr = om.readTree(jsonArr);
                    if (arr.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode node : arr) {
                            String text = node.asText().trim();
                            if (!text.isBlank() && text.length() > 1) {
                                candidates.add(text);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse OCR candidates, falling back to line split: {}", e.getMessage());
                candidates = Arrays.stream(ocrText.split("\\n"))
                        .map(String::trim)
                        .filter(s -> s.length() > 1)
                        .toList();
            }

            return ResponseEntity.ok(Map.of("candidates", candidates));
        } catch (Exception e) {
            log.error("OCR phrase extraction failed", e);
            return bad("OCR failed: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam("q") String query) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");
        if (query == null || query.isBlank()) return bad("q is required");

        List<Long> ids = learningAI.searchPhrases(tenantId, query.trim(), 20);
        if (ids.isEmpty()) return ResponseEntity.ok(Map.of("phrases", List.of(), "count", 0));

        List<GoodPhrase> results = new ArrayList<>();
        for (Long id : ids) {
            phraseService.get(tenantId, id).ifPresent(results::add);
        }
        return ResponseEntity.ok(Map.of(
                "phrases", results.stream().map(this::toMap).toList(),
                "count", results.size()));
    }

    @PostMapping("/export-word")
    public ResponseEntity<byte[]> exportWord(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return ResponseEntity.badRequest().build();

        try {
            List<GoodPhrase> phrases;
            @SuppressWarnings("unchecked")
            List<Integer> rawIds = (List<Integer>) body.getOrDefault("ids", List.of());
            if (rawIds != null && !rawIds.isEmpty()) {
                List<Long> ids = rawIds.stream().map(Integer::longValue).toList();
                phrases = new ArrayList<>();
                for (Long id : ids) {
                    phraseService.get(tenantId, id).ifPresent(phrases::add);
                }
            } else {
                phrases = phraseService.list(tenantId, null, null, null, null, null, null);
            }
            byte[] docBytes = phraseService.exportWord(tenantId, phrases);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=phrases.docx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(docBytes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        List<GoodPhrase> all = phraseService.list(tenantId, null, null, null, null, null, null);
        Map<String, Long> byTheme = new LinkedHashMap<>();
        Map<String, Long> byEmotion = new LinkedHashMap<>();
        Map<String, Long> byMastery = new LinkedHashMap<>();

        for (GoodPhrase p : all) {
            if (p.getTheme() != null) byTheme.merge(p.getTheme(), 1L, Long::sum);
            if (p.getEmotion() != null) byEmotion.merge(p.getEmotion(), 1L, Long::sum);
            String m = p.getMasteryLevel() != null ? p.getMasteryLevel() : "不熟悉";
            byMastery.merge(m, 1L, Long::sum);
        }

        return ResponseEntity.ok(Map.of(
                "total", all.size(),
                "byTheme", byTheme,
                "byEmotion", byEmotion,
                "byMastery", byMastery));
    }

    // ==================== Helpers ====================

    private Map<String, Object> toMap(GoodPhrase p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("content", p.getContent());
        map.put("source", p.getSource());
        map.put("theme", p.getTheme());
        map.put("emotion", p.getEmotion());
        map.put("usageType", p.getUsageType());
        map.put("tags", p.getTags());
        map.put("masteryLevel", p.getMasteryLevel());
        map.put("entryMethod", p.getEntryMethod());
        map.put("language", p.getLanguage());
        map.put("hasImage", p.getImagePath() != null);
        map.put("notebookId", p.getNotebookId());
        map.put("createdTime", p.getCreatedTime() != null ? p.getCreatedTime().toString() : "");
        return map;
    }

    private static ResponseEntity<Map<String, Object>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    @SuppressWarnings("unchecked")
    private static String stringField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s && !s.isBlank() ? s : null;
    }
}
