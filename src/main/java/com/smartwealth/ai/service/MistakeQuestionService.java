package com.smartwealth.ai.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.smartwealth.ai.config.OssConfig;
import com.smartwealth.ai.domain.MistakeQuestion;
import com.smartwealth.ai.repository.MistakeQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MistakeQuestionService {

    private static final Logger log = LoggerFactory.getLogger(MistakeQuestionService.class);

    private final MistakeQuestionRepository repository;
    private final LearningAIService learningAI;
    private final OSS ossClient;
    private final OssConfig.OssProperties ossProperties;

    public MistakeQuestionService(MistakeQuestionRepository repository,
                                  LearningAIService learningAI,
                                  OSS ossClient,
                                  OssConfig.OssProperties ossProperties) {
        this.repository = repository;
        this.learningAI = learningAI;
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    // ==================== Create single ====================

    @Transactional
    public MistakeQuestion create(String tenantId, MistakeInput input, byte[] imageBytes, String contentType) {
        MistakeQuestion entity = new MistakeQuestion();
        entity.setTenantId(tenantId);
        entity.setNotebookId(input.notebookId);
        entity.setSubject(input.subject);
        entity.setQuestionType(input.questionType);
        entity.setGradeLevel(input.gradeLevel);
        entity.setContent(input.content);
        entity.setCorrectAnswer(input.correctAnswer);
        entity.setErrorReason(input.errorReason);
        entity.setSource(input.source);
        entity.setMasteryLevel(input.masteryLevel != null ? input.masteryLevel : "不熟悉");

        if (input.handwriteRemoved != null && input.handwriteRemoved) {
            entity.setHandwriteRemoved(true);
        }

        entity = repository.save(entity);

        // Upload original image to OSS
        if (imageBytes != null) {
            String origKey = tenantId + "/mistakes/" + entity.getId() + "_orig.jpg";
            uploadToOss(origKey, imageBytes, contentType);
            entity.setOrigImage(origKey);
        }

        // Index vector
        String searchable = buildSearchableText(entity);
        try {
            String vectorId = learningAI.indexMistakeText(tenantId, entity.getId(), searchable);
            entity.setVectorId(vectorId);
        } catch (Exception e) {
            log.warn("Failed to index mistake vector for id={}: {}", entity.getId(), e.getMessage());
        }

        entity.setUpdatedTime(LocalDateTime.now());
        return repository.save(entity);
    }

    // ==================== Batch create from page split ====================

    @Transactional
    public List<MistakeQuestion> batchCreate(String tenantId, Long notebookId, String source,
                                              String gradeLevel, List<MistakeInput> inputs,
                                              String pageImageKey, byte[] pageImage, String contentType) {
        List<MistakeQuestion> results = new ArrayList<>();
        for (MistakeInput input : inputs) {
            MistakeQuestion entity = new MistakeQuestion();
            entity.setTenantId(tenantId);
            entity.setNotebookId(notebookId);
            entity.setSource(source);
            entity.setGradeLevel(gradeLevel);
            entity.setSubject(input.subject);
            entity.setQuestionType(input.questionType);
            entity.setContent(input.content);
            entity.setCorrectAnswer(input.correctAnswer);
            entity.setErrorReason(input.errorReason);
            entity.setMasteryLevel(input.masteryLevel != null ? input.masteryLevel : "不熟悉");
            entity.setHandwriteRemoved(input.handwriteRemoved != null && input.handwriteRemoved);
            if (pageImageKey != null) entity.setPageImageKey(pageImageKey);
            entity = repository.save(entity);

            // Upload page image as reference for the first item; subsequent items share
            if (pageImage != null && results.isEmpty()) {
                String origKey = tenantId + "/mistakes/" + entity.getId() + "_page.jpg";
                uploadToOss(origKey, pageImage, contentType);
                entity.setOrigImage(origKey);
            }

            // Index vector
            String searchable = buildSearchableText(entity);
            try {
                entity.setVectorId(learningAI.indexMistakeText(tenantId, entity.getId(), searchable));
            } catch (Exception e) {
                log.warn("Vector index failed for mistake id={}: {}", entity.getId(), e.getMessage());
            }
            repository.save(entity);
            results.add(entity);
        }
        return results;
    }

    // ==================== AI classify + create ====================

    @Transactional
    public MistakeQuestion createWithAI(String tenantId, Long notebookId, String source,
                                         String gradeLevel, byte[] imageBytes, String contentType) {
        String ocrText = ""; // OCR handled by split page, or we could do single OCR here
        LearningAIService.MistakeClassification cls = learningAI.classifyMistake(ocrText, imageBytes);

        MistakeInput input = new MistakeInput();
        input.notebookId = notebookId;
        input.source = source;
        input.gradeLevel = gradeLevel;
        input.subject = cls.subject();
        input.questionType = cls.questionType();
        input.errorReason = cls.errorReason();
        input.correctAnswer = cls.suggestedAnswer();

        return create(tenantId, input, imageBytes, contentType);
    }

    // ==================== Read ====================

    public List<MistakeQuestion> list(String tenantId, Long notebookId, String subject,
                                       String questionType, String gradeLevel, String masteryLevel) {
        return repository.findByFilters(tenantId, notebookId, subject, questionType, gradeLevel, masteryLevel);
    }

    public Optional<MistakeQuestion> get(String tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId);
    }

    // ==================== Update ====================

    @Transactional
    public MistakeQuestion update(String tenantId, Long id, MistakeInput input) {
        MistakeQuestion entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Mistake question not found: " + id));

        if (input.notebookId != null) entity.setNotebookId(input.notebookId);
        if (input.subject != null) entity.setSubject(input.subject);
        if (input.questionType != null) entity.setQuestionType(input.questionType);
        if (input.gradeLevel != null) entity.setGradeLevel(input.gradeLevel);
        if (input.content != null) entity.setContent(input.content);
        if (input.correctAnswer != null) entity.setCorrectAnswer(input.correctAnswer);
        if (input.errorReason != null) entity.setErrorReason(input.errorReason);
        if (input.source != null) entity.setSource(input.source);
        if (input.masteryLevel != null) entity.setMasteryLevel(input.masteryLevel);

        // Re-index if content changed
        if (entity.getVectorId() != null) {
            learningAI.removeVector(entity.getVectorId());
        }
        String searchable = buildSearchableText(entity);
        entity.setVectorId(learningAI.indexMistakeText(tenantId, entity.getId(), searchable));

        return repository.save(entity);
    }

    // ==================== Delete ====================

    @Transactional
    public void delete(String tenantId, Long id) {
        MistakeQuestion entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Mistake question not found: " + id));

        if (entity.getVectorId() != null) {
            learningAI.removeVector(entity.getVectorId());
        }
        if (entity.getOrigImage() != null) {
            try { ossClient.deleteObject(ossProperties.getBucket(), entity.getOrigImage()); } catch (Exception ignored) {}
        }
        if (entity.getCleanImage() != null) {
            try { ossClient.deleteObject(ossProperties.getBucket(), entity.getCleanImage()); } catch (Exception ignored) {}
        }
        repository.delete(entity);
    }

    // ==================== Helpers ====================

    private void uploadToOss(String key, byte[] bytes, String contentType) {
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(contentType != null ? contentType : "image/jpeg");
            ossClient.putObject(ossProperties.getBucket(), key, new ByteArrayInputStream(bytes), meta);
        } catch (Exception e) {
            log.warn("Failed to upload to OSS key={}: {}", key, e.getMessage());
        }
    }

    private static String buildSearchableText(MistakeQuestion m) {
        StringBuilder sb = new StringBuilder();
        if (m.getSubject() != null) sb.append(m.getSubject()).append(" ");
        if (m.getQuestionType() != null) sb.append(m.getQuestionType()).append(" ");
        if (m.getGradeLevel() != null) sb.append(m.getGradeLevel()).append(" ");
        if (m.getContent() != null) sb.append(m.getContent()).append(" ");
        if (m.getErrorReason() != null) sb.append(m.getErrorReason());
        return sb.toString().trim();
    }

    // ==================== Input record ====================

    public static class MistakeInput {
        public Long notebookId;
        public String subject;
        public String questionType;
        public String gradeLevel;
        public String content;
        public String correctAnswer;
        public String errorReason;
        public String source;
        public String masteryLevel;
        public Boolean handwriteRemoved;
    }
}
