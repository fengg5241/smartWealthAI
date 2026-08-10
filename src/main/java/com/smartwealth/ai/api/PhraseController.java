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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

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
            @RequestParam(value = "imageKey", required = false) String imageKey,
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
            input.imageKey = blankToNull(imageKey);

            byte[] imageBytes = file != null ? file.getBytes() : null;
            String contentType = file != null ? file.getContentType() : null;
            // entryMethod hint: if user explicitly tagged as "photo" but no file, still treat as text
            if (imageBytes != null && blankToNull(entryMethod) == null) {
                // image provided without explicit entryMethod — will be auto-set in service
            }

            GoodPhrase phrase = phraseService.create(tenantId, input, imageBytes, contentType);
            CompletableFuture.runAsync(() -> {
                try { reviewService.schedulePhraseForReview(tenantId, phrase.getId()); } catch (Exception ignored) {}
            });
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
            @RequestParam(value = "standard", required = false, defaultValue = "PSLE") String standard,
            @RequestParam(value = "cropX", required = false) Integer cropX,
            @RequestParam(value = "cropY", required = false) Integer cropY,
            @RequestParam(value = "cropW", required = false) Integer cropW,
            @RequestParam(value = "cropH", required = false) Integer cropH) {

        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");
        if (file == null || file.isEmpty()) return bad("Image is required");

        try {
            byte[] imageBytes = file.getBytes();

            // Apply crop if all coordinates provided
            if (cropX != null && cropY != null && cropW != null && cropH != null) {
                imageBytes = cropImage(imageBytes, cropX, cropY, cropW, cropH);
            }

            // Compress for faster AI transfer and OSS upload
            byte[] compressed = compressImage(imageBytes);

            // Upload to OSS and OCR in parallel (independent operations on same bytes)
            String pageImageKey = tenantId + "/phrases/pages/" + UUID.randomUUID() + ".jpg";
            CompletableFuture<Void> ossFuture = CompletableFuture.runAsync(() ->
                uploadToOss(pageImageKey, compressed, "image/jpeg"));

            String ocrText = ocrService.ocrImage(compressed);

            // Wait for OSS upload to finish (should already be done by now)
            try { ossFuture.get(10, TimeUnit.SECONDS); } catch (Exception e) {
                log.warn("OSS upload for {} did not complete in time: {}", pageImageKey, e.getMessage());
            }

            if (ocrText == null || ocrText.isBlank()) {
                return ResponseEntity.ok(Map.of("candidates", List.of(), "pageImageKey", pageImageKey));
            }

            // Build grading-standard-specific filter prompt
            String gradingCriteria = switch (standard.toUpperCase()) {
                case "O_LEVEL", "OLEVEL" -> """
                    O Level (Secondary 4): Sophisticated vocabulary, effective metaphors,
                    nuanced emotional language, rhetorical devices, mature sentence
                    structure. Look for precise word choices, figurative language,
                    and stylistic control.""";
                case "A_LEVEL", "ALEVEL" -> """
                    A Level (JC/MI Year 2): Advanced literary techniques, precise diction,
                    complex rhetorical strategies, original voice. Look for striking word
                    choices, layered meaning, and masterful language for effect.""";
                default -> """
                    PSLE (Primary 6): Vivid adjectives, similes, emotional expressions,
                    appropriate idioms, varied sentence starters, and noteworthy word
                    choices. A sentence is worth collecting if any part of it shows
                    writing effort beyond basic description.""";
            };

            // Split OCR text into complete sentences
            List<String> sentences = splitSentences(ocrText);
            if (sentences.isEmpty()) {
                sentences = List.of(ocrText.trim());
            }

            // Build numbered sentence list for the prompt
            StringBuilder numberedSentences = new StringBuilder();
            for (int i = 0; i < sentences.size(); i++) {
                numberedSentences.append(i + 1).append(". ").append(sentences.get(i)).append("\n");
            }

            String prompt = """
                You are a writing evaluator. Below are complete sentences extracted from
                a student's notebook, each prefixed with a number. Select every sentence
                that has noteworthy writing quality — interesting vocabulary, vivid imagery,
                emotional depth, figurative language, strong word choices, or varied sentence
                structure.

                Key: a sentence is worth collecting if ANY of these is true:
                - It uses a striking or unusual word combination (e.g. "accomplished liar",
                  "bitter sweetness", "deafening silence")
                - It expresses emotion in a vivid or relatable way
                - It uses simile, metaphor, personification, or other figurative language
                - It contains a descriptive adjective or adverb that makes the writing stand out
                - It has a sentence structure that shows effort (varied length, clause stacking)

                A single strong word choice in an otherwise ordinary sentence is enough.
                For example, for these sentences:
                1. The sky was grey.
                2. She felt like an accomplished liar — polished, professional, and utterly false.
                3. He went to the store.

                You should select [2], because "accomplished liar" and "polished, professional,
                and utterly false" are striking word combinations. Sentence 1 and 3 are too
                generic.

                %s

                Numbered sentences:
                %s
                Return ONLY a JSON array of the numbers (integers) of the qualifying sentences.
                Example: [2]
                If none qualify, return an empty array [].""".formatted(gradingCriteria, numberedSentences.toString());

            String response = learningAI.callTextModelRaw("qwen-turbo", prompt, 200);

            // Parse JSON array of sentence numbers, map back to full sentence text
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
                            if (node.isInt()) {
                                int idx = node.asInt() - 1;
                                if (idx >= 0 && idx < sentences.size()) {
                                    candidates.add(sentences.get(idx));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse OCR selection by numbers, falling back to all sentences: {}", e.getMessage());
                candidates = sentences;
            }

            return ResponseEntity.ok(Map.of("candidates", candidates, "pageImageKey", pageImageKey));
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

    private void uploadToOss(String key, byte[] bytes, String contentType) {
        var meta = new com.aliyun.oss.model.ObjectMetadata();
        meta.setContentType(contentType != null ? contentType : "image/jpeg");
        ossClient.putObject(ossProperties.getBucket(), key,
                new java.io.ByteArrayInputStream(bytes), meta);
    }

    /** Crop image to the given rectangle (in source image pixels). Outputs as JPEG. */
    static byte[] cropImage(byte[] src, int x, int y, int w, int h) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(src));
            if (img == null) return src;
            int iw = img.getWidth(), ih = img.getHeight();
            x = Math.max(0, Math.min(x, iw - 1));
            y = Math.max(0, Math.min(y, ih - 1));
            w = Math.min(w, iw - x);
            h = Math.min(h, ih - y);
            BufferedImage cropped = img.getSubimage(x, y, w, h);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(cropped, "jpeg", out);
            return out.toByteArray();
        } catch (Exception e) {
            return src;
        }
    }

    /** Compress image to max 1024px wide, JPEG quality 75%. */
    static byte[] compressImage(byte[] original) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
            if (src == null) return original;

            int w = src.getWidth();
            int h = src.getHeight();
            int maxW = 1024;
            if (w <= maxW) return original;

            int newH = (int) ((double) h / w * maxW);

            BufferedImage scaled = new BufferedImage(maxW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, maxW, newH, null);
            g.dispose();

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.75f);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(scaled, null, null), param);
            }
            writer.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Image compression failed, using original: {}", e.getMessage());
            return original;
        }
    }

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

    /**
     * Split text into complete sentences using sentence-ending punctuation.
     * Delimiters: . ! ? (English) and 。！？ (Chinese).
     */
    private static List<String> splitSentences(String text) {
        List<String> result = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[^。！？.!?]+[。！？.!?]+");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (sentence.length() > 1) {
                result.add(sentence);
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            String remainder = text.substring(lastEnd).trim();
            if (remainder.length() > 1) {
                result.add(remainder);
            }
        }
        return result;
    }
}
