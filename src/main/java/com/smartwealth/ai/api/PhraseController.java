package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.GoodPhrase;
import com.smartwealth.ai.service.GoodPhraseService;
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
    private final com.aliyun.oss.OSS ossClient;
    private final com.smartwealth.ai.config.OssConfig.OssProperties ossProperties;

    public PhraseController(GoodPhraseService phraseService,
                            com.aliyun.oss.OSS ossClient,
                            com.smartwealth.ai.config.OssConfig.OssProperties ossProperties) {
        this.phraseService = phraseService;
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

            byte[] imageBytes = file != null ? file.getBytes() : null;
            String contentType = file != null ? file.getContentType() : null;

            GoodPhrase phrase = phraseService.create(tenantId, input, imageBytes, contentType);
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
            @RequestParam(value = "masteryLevel", required = false) String masteryLevel) {

        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        List<GoodPhrase> phrases = phraseService.list(tenantId,
                blankToNull(theme), blankToNull(emotion), blankToNull(masteryLevel));
        return ResponseEntity.ok(Map.of(
                "phrases", phrases.stream().map(this::toMap).toList(),
                "themes", phraseService.getThemes(tenantId),
                "emotions", phraseService.getEmotions(tenantId),
                "count", phrases.size()));
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
        map.put("hasImage", p.getImagePath() != null);
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
