package com.smartwealth.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mistake_question")
public class MistakeQuestion extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notebook_id")
    private Long notebookId;

    @Column(name = "subject", length = 30)
    private String subject;

    @Column(name = "question_type", length = 60)
    private String questionType;

    @Column(name = "grade_level", length = 20)
    private String gradeLevel;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "correct_answer", columnDefinition = "TEXT")
    private String correctAnswer;

    @Column(name = "error_reason", length = 100)
    private String errorReason;

    @Column(name = "source", length = 255)
    private String source;

    @Column(name = "mastery_level", length = 20)
    private String masteryLevel = "不熟悉";

    @Column(name = "orig_image", length = 500)
    private String origImage;

    @Column(name = "clean_image", length = 500)
    private String cleanImage;

    @Column(name = "handwrite_removed")
    private Boolean handwriteRemoved = false;

    @Column(name = "page_image_key", length = 255)
    private String pageImageKey;

    @Column(name = "vector_id", length = 100)
    private String vectorId;

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        if (this.createdTime == null) this.createdTime = now;
        if (this.updatedTime == null) this.updatedTime = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getNotebookId() { return notebookId; }
    public void setNotebookId(Long notebookId) { this.notebookId = notebookId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getGradeLevel() { return gradeLevel; }
    public void setGradeLevel(String gradeLevel) { this.gradeLevel = gradeLevel; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getMasteryLevel() { return masteryLevel; }
    public void setMasteryLevel(String masteryLevel) { this.masteryLevel = masteryLevel; }

    public String getOrigImage() { return origImage; }
    public void setOrigImage(String origImage) { this.origImage = origImage; }

    public String getCleanImage() { return cleanImage; }
    public void setCleanImage(String cleanImage) { this.cleanImage = cleanImage; }

    public Boolean getHandwriteRemoved() { return handwriteRemoved; }
    public void setHandwriteRemoved(Boolean handwriteRemoved) { this.handwriteRemoved = handwriteRemoved; }

    public String getPageImageKey() { return pageImageKey; }
    public void setPageImageKey(String pageImageKey) { this.pageImageKey = pageImageKey; }

    public String getVectorId() { return vectorId; }
    public void setVectorId(String vectorId) { this.vectorId = vectorId; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
