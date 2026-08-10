package com.smartwealth.ai.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.smartwealth.ai.config.OssConfig;
import com.smartwealth.ai.domain.GoodPhrase;
import com.smartwealth.ai.repository.GoodPhraseRepository;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class GoodPhraseService {

    private static final Logger log = LoggerFactory.getLogger(GoodPhraseService.class);

    private final GoodPhraseRepository repository;
    private final LearningAIService learningAI;
    private final OSS ossClient;
    private final OssConfig.OssProperties ossProperties;

    public GoodPhraseService(GoodPhraseRepository repository,
                             LearningAIService learningAI,
                             OSS ossClient,
                             OssConfig.OssProperties ossProperties) {
        this.repository = repository;
        this.learningAI = learningAI;
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    @Transactional
    public GoodPhrase create(String tenantId, PhraseInput input, byte[] imageBytes, String contentType) {
        GoodPhrase entity = new GoodPhrase();
        entity.setTenantId(tenantId);
        entity.setNotebookId(input.notebookId);
        entity.setContent(input.content);
        entity.setSource(input.source);
        entity.setMasteryLevel(input.masteryLevel != null ? input.masteryLevel : "不熟悉");
        entity.setEntryMethod(imageBytes != null || input.imageKey != null ? "photo" : "text");

        // Language detection (fast, no network call)
        if (!isEmpty(input.language)) {
            entity.setLanguage(input.language);
        } else {
            entity.setLanguage(detectLanguage(input.content));
        }

        // Use pre-set tags if provided, otherwise leave blank for async enrichment
        if (!isEmpty(input.theme) || !isEmpty(input.emotion) || !isEmpty(input.usageType) || !isEmpty(input.tags)) {
            entity.setTheme(input.theme);
            entity.setEmotion(input.emotion);
            entity.setUsageType(input.usageType);
            entity.setTags(input.tags);
        }

        if (input.imageKey != null) {
            entity.setImagePath(input.imageKey);
        } else if (imageBytes != null) {
            entity = repository.save(entity);
            String key = tenantId + "/phrases/" + entity.getId() + ".jpg";
            uploadToOss(key, imageBytes, contentType);
            entity.setImagePath(key);
        }

        entity = repository.save(entity);

        // Async: AI tagging + vector indexing (don't block the response)
        final Long phraseId = entity.getId();
        final boolean needsAiTag = isEmpty(input.theme) && isEmpty(input.emotion)
                && isEmpty(input.usageType) && isEmpty(input.tags);
        CompletableFuture.runAsync(() -> enrichPhrase(tenantId, phraseId, needsAiTag));

        return entity;
    }

    private void enrichPhrase(String tenantId, Long phraseId, boolean needsAiTag) {
        try {
            GoodPhrase entity = repository.findById(phraseId).orElse(null);
            if (entity == null) return;

            if (needsAiTag) {
                try {
                    LearningAIService.PhraseTags aiTags = learningAI.tagPhrase(entity.getContent());
                    entity.setTheme(aiTags.theme());
                    entity.setEmotion(aiTags.emotion());
                    entity.setUsageType(aiTags.usageType());
                    entity.setTags(aiTags.tags());
                } catch (Exception e) {
                    log.warn("Async AI tagging failed for phrase id={}: {}", phraseId, e.getMessage());
                }
            }

            String searchable = buildSearchableText(entity);
            try {
                entity.setVectorId(learningAI.indexPhraseText(tenantId, phraseId, searchable));
            } catch (Exception e) {
                log.warn("Async vector index failed for phrase id={}: {}", phraseId, e.getMessage());
            }

            repository.save(entity);
        } catch (Exception e) {
            log.warn("Async enrichment failed for phrase id={}: {}", phraseId, e.getMessage());
        }
    }

    public List<GoodPhrase> list(String tenantId, String theme, String emotion, String masteryLevel,
                               String language, String tags, Long notebookId) {
        List<GoodPhrase> phrases = repository.findByFilters(tenantId,
                emptyToNull(theme), emptyToNull(emotion), emptyToNull(masteryLevel),
                emptyToNull(language), notebookId);

        // Post-filter by tags: AND logic
        if (tags != null && !tags.isBlank()) {
            List<String> required = Arrays.stream(tags.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (!required.isEmpty()) {
                phrases = phrases.stream()
                        .filter(p -> {
                            String pt = p.getTags();
                            if (pt == null || pt.isBlank()) return false;
                            Set<String> tagSet = Arrays.stream(pt.split(","))
                                    .map(String::trim).collect(Collectors.toSet());
                            return required.stream().allMatch(tagSet::contains);
                        })
                        .toList();
            }
        }
        return phrases;
    }

    public Optional<GoodPhrase> get(String tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId);
    }

    public List<String> getThemes(String tenantId) {
        return repository.findDistinctThemes(tenantId);
    }

    public List<String> getEmotions(String tenantId) {
        return repository.findDistinctEmotions(tenantId);
    }

    public List<String> getTags(String tenantId) {
        return repository.findDistinctTags(tenantId);
    }

    public List<String> getLanguages(String tenantId) {
        return repository.findDistinctLanguages(tenantId);
    }

    @Transactional
    public GoodPhrase update(String tenantId, Long id, PhraseInput input) {
        GoodPhrase entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Phrase not found: " + id));

        if (input.content != null) entity.setContent(input.content);
        if (input.source != null) entity.setSource(input.source);
        if (input.theme != null) entity.setTheme(input.theme);
        if (input.emotion != null) entity.setEmotion(input.emotion);
        if (input.usageType != null) entity.setUsageType(input.usageType);
        if (input.tags != null) entity.setTags(input.tags);
        if (input.masteryLevel != null) entity.setMasteryLevel(input.masteryLevel);
        if (input.language != null) entity.setLanguage(input.language);
        if (input.notebookId != null) entity.setNotebookId(input.notebookId == 0 ? null : input.notebookId);

        if (entity.getVectorId() != null) learningAI.removeVector(entity.getVectorId());
        entity.setVectorId(learningAI.indexPhraseText(tenantId, entity.getId(), buildSearchableText(entity)));

        return repository.save(entity);
    }

    @Transactional
    public void delete(String tenantId, Long id) {
        GoodPhrase entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Phrase not found: " + id));

        if (entity.getVectorId() != null) learningAI.removeVector(entity.getVectorId());
        if (entity.getImagePath() != null) {
            try { ossClient.deleteObject(ossProperties.getBucket(), entity.getImagePath()); } catch (Exception ignored) {}
        }
        repository.delete(entity);
    }

    public byte[] exportWord(String tenantId, List<GoodPhrase> phrases) {
        if (phrases.isEmpty()) throw new IllegalArgumentException("No phrases to export");

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.setText("好词好句");

            XWPFParagraph subtitle = doc.createParagraph();
            subtitle.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun subRun = subtitle.createRun();
            subRun.setFontSize(10);
            subRun.setColor("666666");
            subRun.setText("导出日期：" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
                    " | 共计：" + phrases.size() + " 条");

            doc.createParagraph();

            Map<String, List<GoodPhrase>> byTheme = new LinkedHashMap<>();
            for (GoodPhrase p : phrases) {
                String theme = p.getTheme() != null ? p.getTheme() : "未分类";
                byTheme.computeIfAbsent(theme, k -> new ArrayList<>()).add(p);
            }

            for (var entry : byTheme.entrySet()) {
                XWPFParagraph catPara = doc.createParagraph();
                catPara.setSpacingBefore(300);
                XWPFRun catRun = catPara.createRun();
                catRun.setBold(true);
                catRun.setFontSize(14);
                catRun.setColor("2563EB");
                catRun.setText("【" + entry.getKey() + "】");

                for (GoodPhrase p : entry.getValue()) {
                    XWPFParagraph pPara = doc.createParagraph();
                    XWPFRun pRun = pPara.createRun();
                    pRun.setFontSize(12);
                    pRun.setText("◆ " + (p.getContent() != null ? p.getContent() : ""));

                    StringBuilder meta = new StringBuilder();
                    if (p.getEmotion() != null) meta.append("情感：").append(p.getEmotion()).append("  ");
                    if (p.getUsageType() != null) meta.append("用法：").append(p.getUsageType()).append("  ");
                    if (p.getSource() != null) meta.append("来源：").append(p.getSource());
                    if (meta.length() > 0) {
                        XWPFParagraph mPara = doc.createParagraph();
                        XWPFRun mRun = mPara.createRun();
                        mRun.setFontSize(9);
                        mRun.setColor("888888");
                        mRun.setText(meta.toString().trim());
                    }
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("Phrase Word export failed", e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    private void uploadToOss(String key, byte[] bytes, String contentType) {
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(contentType != null ? contentType : "image/jpeg");
            ossClient.putObject(ossProperties.getBucket(), key, new ByteArrayInputStream(bytes), meta);
        } catch (Exception e) {
            log.warn("OSS upload failed key={}: {}", key, e.getMessage());
        }
    }

    private static String buildSearchableText(GoodPhrase p) {
        StringBuilder sb = new StringBuilder();
        if (p.getContent() != null) sb.append(p.getContent()).append(" ");
        if (p.getTheme() != null) sb.append(p.getTheme()).append(" ");
        if (p.getEmotion() != null) sb.append(p.getEmotion()).append(" ");
        if (p.getUsageType() != null) sb.append(p.getUsageType()).append(" ");
        if (p.getTags() != null) sb.append(p.getTags()).append(" ");
        if (p.getSource() != null) sb.append(p.getSource());
        return sb.toString().trim();
    }

    private static boolean isEmpty(String s) { return s == null || s.isBlank(); }
    private static String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    static String detectLanguage(String text) {
        if (text == null || text.isBlank()) return "zh";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
                return "zh";
            }
        }
        return "en";
    }

    public static class PhraseInput {
        public Long notebookId;
        public String content;
        public String source;
        public String theme;
        public String emotion;
        public String usageType;
        public String tags;
        public String masteryLevel;
        public String language;
        public String imageKey;
    }
}
