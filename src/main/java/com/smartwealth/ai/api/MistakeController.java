package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.MistakeQuestion;
import com.smartwealth.ai.service.*;
import com.smartwealth.ai.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

@RestController
@RequestMapping("/api/mistakes")
public class MistakeController {

    private static final Logger log = LoggerFactory.getLogger(MistakeController.class);

    private final MistakeQuestionService mistakeService;
    private final ReviewService reviewService;
    private final LearningAIService learningAI;
    private final WordExportService wordExportService;
    private final com.aliyun.oss.OSS ossClient;
    private final com.smartwealth.ai.config.OssConfig.OssProperties ossProperties;

    public MistakeController(MistakeQuestionService mistakeService,
                             ReviewService reviewService, LearningAIService learningAI,
                             WordExportService wordExportService,
                             com.aliyun.oss.OSS ossClient,
                             com.smartwealth.ai.config.OssConfig.OssProperties ossProperties) {
        this.mistakeService = mistakeService;
        this.reviewService = reviewService;
        this.learningAI = learningAI;
        this.wordExportService = wordExportService;
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    // ==================== Page split ====================

    @PostMapping("/split-page")
    public ResponseEntity<Map<String, Object>> splitPage(@RequestParam("image") MultipartFile file,
            @RequestParam(value = "cropX", required = false) Integer cropX,
            @RequestParam(value = "cropY", required = false) Integer cropY,
            @RequestParam(value = "cropW", required = false) Integer cropW,
            @RequestParam(value = "cropH", required = false) Integer cropH) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            byte[] imageBytes = file.getBytes();

            // Apply crop if all coordinates provided
            if (cropX != null && cropY != null && cropW != null && cropH != null) {
                long t0 = System.currentTimeMillis();
                imageBytes = cropImage(imageBytes, cropX, cropY, cropW, cropH);
                log.info("Image cropped: {}x{}+{}+{} -> {}KB in {}ms",
                        cropW, cropH, cropX, cropY, imageBytes.length / 1024, System.currentTimeMillis() - t0);
            }

            // Compress for faster AI transfer and OSS upload
            long t0 = System.currentTimeMillis();
            byte[] compressed = compressImage(imageBytes);
            log.info("Image compressed: {}KB -> {}KB in {}ms",
                    imageBytes.length / 1024, compressed.length / 1024, System.currentTimeMillis() - t0);

            List<LearningAIService.QuestionSplit> questions = learningAI.splitPageToQuestions(compressed);

            String pageImageBase64 = Base64.getEncoder().encodeToString(compressed);
            return ResponseEntity.ok(Map.of("questions", questions, "pageImage", pageImageBase64));
        } catch (Exception e) {
            log.error("Page split failed", e);
            return bad(e.getMessage());
        }
    }

    // ==================== AI classify ====================

    @PostMapping("/classify")
    public ResponseEntity<Map<String, Object>> classify(@RequestParam("image") MultipartFile file,
                                                         @RequestParam(value = "ocrText", required = false, defaultValue = "") String ocrText) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            LearningAIService.MistakeClassification cls =
                    learningAI.classifyMistake(ocrText.isBlank() ? null : ocrText, file.getBytes());
            return ResponseEntity.ok(Map.of(
                    "subject", cls.subject(),
                    "questionType", cls.questionType(),
                    "errorReason", cls.errorReason(),
                    "suggestedAnswer", cls.suggestedAnswer()));
        } catch (Exception e) {
            log.error("Classify failed", e);
            return bad(e.getMessage());
        }
    }

    // ==================== AI remove handwriting ====================

    @PostMapping("/remove-handwriting")
    public ResponseEntity<Map<String, Object>> removeHandwriting(@RequestParam("image") MultipartFile file) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            String cleanText = learningAI.removeHandwriting(file.getBytes());
            return ResponseEntity.ok(Map.of("cleanText", cleanText));
        } catch (Exception e) {
            log.error("Remove handwriting failed", e);
            return bad(e.getMessage());
        }
    }

    // ==================== CRUD ====================

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam(value = "image", required = false) MultipartFile file,
            @RequestParam(value = "notebookId", required = false) Long notebookId,
            @RequestParam(value = "subject", required = false, defaultValue = "") String subject,
            @RequestParam(value = "questionType", required = false, defaultValue = "") String questionType,
            @RequestParam(value = "gradeLevel", required = false, defaultValue = "") String gradeLevel,
            @RequestParam(value = "content", required = false, defaultValue = "") String content,
            @RequestParam(value = "correctAnswer", required = false, defaultValue = "") String correctAnswer,
            @RequestParam(value = "errorReason", required = false, defaultValue = "") String errorReason,
            @RequestParam(value = "source", required = false, defaultValue = "") String source,
            @RequestParam(value = "masteryLevel", required = false, defaultValue = "不熟悉") String masteryLevel,
            @RequestParam(value = "handwriteRemoved", required = false, defaultValue = "false") boolean handwriteRemoved) {

        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            MistakeQuestionService.MistakeInput input = new MistakeQuestionService.MistakeInput();
            input.notebookId = notebookId;
            input.subject = blankToNull(subject);
            input.questionType = blankToNull(questionType);
            input.gradeLevel = blankToNull(gradeLevel);
            input.content = blankToNull(content);
            input.correctAnswer = blankToNull(correctAnswer);
            input.errorReason = blankToNull(errorReason);
            input.source = blankToNull(source);
            input.masteryLevel = masteryLevel;
            input.handwriteRemoved = handwriteRemoved;

            byte[] imageBytes = file != null ? file.getBytes() : null;
            String contentType = file != null ? file.getContentType() : null;

            MistakeQuestion mq = mistakeService.create(tenantId, input, imageBytes, contentType);

            // Auto-create review schedule
            reviewService.scheduleForReview(tenantId, mq.getId());

            return ResponseEntity.ok(toMap(mq));
        } catch (Exception e) {
            log.error("Create mistake failed", e);
            return bad(e.getMessage());
        }
    }

    /**
     * Batch create after page split — accept list of mistake inputs with shared source/gradeLevel/notebookId.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchCreate(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            Long notebookId = body.get("notebookId") instanceof Number n ? n.longValue() : null;
            String source = body.get("source") instanceof String s && !s.isBlank() ? s : null;
            String gradeLevel = body.get("gradeLevel") instanceof String s && !s.isBlank() ? s : null;
            String pageImageBase64 = body.get("pageImage") instanceof String s && !s.isBlank() ? s : null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("questions", List.of());

            List<MistakeQuestionService.MistakeInput> inputs = new ArrayList<>();
            for (Map<String, Object> item : items) {
                MistakeQuestionService.MistakeInput input = new MistakeQuestionService.MistakeInput();
                input.notebookId = notebookId;
                input.source = source;
                input.gradeLevel = gradeLevel;
                input.subject = stringField(item, "subject");
                input.questionType = stringField(item, "questionType");
                input.content = stringField(item, "content");
                input.correctAnswer = stringField(item, "correctAnswer");
                input.errorReason = stringField(item, "errorReason");
                input.masteryLevel = stringField(item, "masteryLevel");
                input.handwriteRemoved = Boolean.TRUE.equals(item.get("handwriteRemoved"));
                inputs.add(input);
            }

            // Upload page image to OSS before batch create (with timeout).
            // If OSS fails or times out, proceed without pageKey — View Original just won't work.
            String pageKey = null;
            if (pageImageBase64 != null) {
                final String pk = tenantId + "/mistakes/pages/" + UUID.randomUUID() + ".jpg";
                byte[] pageImageBytes = Base64.getDecoder().decode(pageImageBase64);
                try {
                    CompletableFuture.runAsync(() -> uploadToOss(pk, pageImageBytes, "image/jpeg"))
                            .get(5, TimeUnit.SECONDS);
                    pageKey = pk;
                } catch (TimeoutException e) {
                    log.warn("OSS upload timed out for {}", pk);
                } catch (Exception e) {
                    log.warn("OSS upload failed for {}: {}", pk, e.getMessage());
                }
            }

            List<MistakeQuestion> created = mistakeService.batchCreate(tenantId, notebookId, source, gradeLevel,
                    inputs, pageKey, null, null);

            for (MistakeQuestion mq : created) {
                reviewService.scheduleForReview(tenantId, mq.getId());
            }

            return ResponseEntity.ok(Map.of("created", created.stream().map(this::toMap).toList(), "count", created.size()));
        } catch (Exception e) {
            log.error("Batch create failed", e);
            return bad(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "notebookId", required = false) Long notebookId,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "questionType", required = false) String questionType,
            @RequestParam(value = "gradeLevel", required = false) String gradeLevel,
            @RequestParam(value = "masteryLevel", required = false) String masteryLevel) {

        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        List<MistakeQuestion> mistakes = mistakeService.list(tenantId, notebookId,
                blankToNull(subject), blankToNull(questionType), blankToNull(gradeLevel), blankToNull(masteryLevel));
        return ResponseEntity.ok(Map.of("mistakes", mistakes.stream().map(this::toMap).toList(), "count", mistakes.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        return mistakeService.get(tenantId, id)
                .map(m -> ResponseEntity.ok(toMap(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return ResponseEntity.badRequest().build();

        return mistakeService.get(tenantId, id)
                .filter(m -> m.getOrigImage() != null)
                .map(m -> streamOssImage(m.getOrigImage()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/page-image")
    public ResponseEntity<byte[]> getPageImage(@RequestParam("key") String key) {
        return streamOssImage(key);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            MistakeQuestionService.MistakeInput input = new MistakeQuestionService.MistakeInput();
            if (body.get("notebookId") instanceof Number n) input.notebookId = n.longValue();
            input.subject = stringField(body, "subject");
            input.questionType = stringField(body, "questionType");
            input.gradeLevel = stringField(body, "gradeLevel");
            input.content = stringField(body, "content");
            input.correctAnswer = stringField(body, "correctAnswer");
            input.errorReason = stringField(body, "errorReason");
            input.source = stringField(body, "source");
            input.masteryLevel = stringField(body, "masteryLevel");

            MistakeQuestion updated = mistakeService.update(tenantId, id, input);
            return ResponseEntity.ok(toMap(updated));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Update mistake failed", e);
            return bad(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        try {
            reviewService.removeSchedule(tenantId, id);
            mistakeService.delete(tenantId, id);
            return ResponseEntity.ok(Map.of("message", "Deleted"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== Generate similar question ====================

    @PostMapping("/{id}/generate-similar")
    public ResponseEntity<Map<String, Object>> generateSimilar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        var mq = mistakeService.get(tenantId, id).orElse(null);
        if (mq == null) return ResponseEntity.notFound().build();

        LearningAIService.SimilarQuestion sq = learningAI.generateSimilarQuestion(
                new LearningAIService.MistakeQuestionData(
                        mq.getContent(), mq.getSubject(), mq.getQuestionType(),
                        mq.getGradeLevel(), mq.getErrorReason()));

        return ResponseEntity.ok(Map.of(
                "question", sq.question(), "answer", sq.answer(), "hint", sq.hint()));
    }

    // ==================== Semantic search ====================

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam("q") String query) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");
        if (query == null || query.isBlank()) return bad("q is required");

        List<Long> ids = learningAI.searchMistakes(tenantId, query.trim(), 20);
        if (ids.isEmpty()) return ResponseEntity.ok(Map.of("mistakes", List.of(), "count", 0));

        List<MistakeQuestion> results = new ArrayList<>();
        for (Long id : ids) {
            mistakeService.get(tenantId, id).ifPresent(results::add);
        }
        return ResponseEntity.ok(Map.of("mistakes", results.stream().map(this::toMap).toList(), "count", results.size()));
    }

    // ==================== Stats ====================

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        List<MistakeQuestion> all = mistakeService.list(tenantId, null, null, null, null, null);
        Map<String, Long> bySubject = new LinkedHashMap<>();
        Map<String, Long> byMastery = new LinkedHashMap<>();
        Map<String, Long> byGrade = new LinkedHashMap<>();
        long totalReviewed = reviewService.getTotalReviewedCount(tenantId);

        for (MistakeQuestion m : all) {
            if (m.getSubject() != null) bySubject.merge(m.getSubject(), 1L, Long::sum);
            String mastery = m.getMasteryLevel() != null ? m.getMasteryLevel() : "不熟悉";
            byMastery.merge(mastery, 1L, Long::sum);
            if (m.getGradeLevel() != null) byGrade.merge(m.getGradeLevel(), 1L, Long::sum);
        }

        return ResponseEntity.ok(Map.of(
                "total", all.size(),
                "bySubject", bySubject,
                "byMastery", byMastery,
                "byGrade", byGrade,
                "totalReviewed", totalReviewed));
    }

    @PostMapping("/export-word")
    public ResponseEntity<byte[]> exportWord(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return ResponseEntity.badRequest().build();

        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.getOrDefault("ids", List.of());
        List<Long> ids = rawIds.stream().map(Integer::longValue).toList();
        String mode = body.get("mode") instanceof String s ? s : "questions-only";
        String notebookName = body.get("notebookName") instanceof String s ? s : "错题本";

        try {
            byte[] docBytes = wordExportService.export(tenantId, ids, mode, notebookName);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=mistakes.docx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(docBytes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ==================== Helpers ====================

    private ResponseEntity<byte[]> streamOssImage(String key) {
        try (InputStream is = ossClient.getObject(ossProperties.getBucket(), key).getObjectContent();
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] data = new byte[8192];
            int n;
            while ((n = is.read(data)) != -1) buf.write(data, 0, n);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(buf.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private void uploadToOss(String key, byte[] bytes, String contentType) {
        var meta = new com.aliyun.oss.model.ObjectMetadata();
        meta.setContentType(contentType != null ? contentType : "image/jpeg");
        ossClient.putObject(ossProperties.getBucket(), key, new java.io.ByteArrayInputStream(bytes), meta);
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

    /** Compress image to max 1280px wide, JPEG quality 85%. */
    static byte[] compressImage(byte[] original) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
            if (src == null) return original; // unsupported format, passthrough

            int w = src.getWidth();
            int h = src.getHeight();
            int maxW = 1280;
            if (w <= maxW) return original; // already small enough
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
            param.setCompressionQuality(0.85f);

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

    private Map<String, Object> toMap(MistakeQuestion m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("notebookId", m.getNotebookId());
        map.put("subject", m.getSubject());
        map.put("questionType", m.getQuestionType());
        map.put("gradeLevel", m.getGradeLevel());
        map.put("content", m.getContent());
        map.put("correctAnswer", m.getCorrectAnswer());
        map.put("errorReason", m.getErrorReason());
        map.put("source", m.getSource());
        map.put("masteryLevel", m.getMasteryLevel());
        map.put("handwriteRemoved", m.getHandwriteRemoved());
        map.put("hasImage", m.getOrigImage() != null);
        map.put("pageImageKey", m.getPageImageKey());
        map.put("createdTime", m.getCreatedTime() != null ? m.getCreatedTime().toString() : "");
        map.put("updatedTime", m.getUpdatedTime() != null ? m.getUpdatedTime().toString() : "");
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
