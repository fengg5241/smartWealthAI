package com.smartwealth.ai.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.smartwealth.ai.config.OssConfig;
import com.smartwealth.ai.domain.GoodPhrase;
import com.smartwealth.ai.repository.GoodPhraseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.*;

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
        entity.setEntryMethod(imageBytes != null ? "photo" : "text");

        // AI auto-tag if tags are empty
        if (isEmpty(input.theme) && isEmpty(input.emotion) && isEmpty(input.usageType) && isEmpty(input.tags)) {
            LearningAIService.PhraseTags aiTags = learningAI.tagPhrase(input.content);
            entity.setTheme(aiTags.theme());
            entity.setEmotion(aiTags.emotion());
            entity.setUsageType(aiTags.usageType());
            entity.setTags(aiTags.tags());
        } else {
            entity.setTheme(input.theme);
            entity.setEmotion(input.emotion);
            entity.setUsageType(input.usageType);
            entity.setTags(input.tags);
        }

        if (imageBytes != null) {
            entity = repository.save(entity);
            String key = tenantId + "/phrases/" + entity.getId() + ".jpg";
            uploadToOss(key, imageBytes, contentType);
            entity.setImagePath(key);
        }

        entity = repository.save(entity);

        // Vector index
        String searchable = buildSearchableText(entity);
        try {
            entity.setVectorId(learningAI.indexPhraseText(tenantId, entity.getId(), searchable));
        } catch (Exception e) {
            log.warn("Vector index failed for phrase id={}: {}", entity.getId(), e.getMessage());
        }

        return repository.save(entity);
    }

    public List<GoodPhrase> list(String tenantId, String theme, String emotion, String masteryLevel) {
        return repository.findByFilters(tenantId,
                emptyToNull(theme), emptyToNull(emotion), emptyToNull(masteryLevel));
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

    public static class PhraseInput {
        public Long notebookId;
        public String content;
        public String source;
        public String theme;
        public String emotion;
        public String usageType;
        public String tags;
        public String masteryLevel;
    }
}
