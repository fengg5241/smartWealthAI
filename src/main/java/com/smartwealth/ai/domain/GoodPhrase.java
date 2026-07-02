package com.smartwealth.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "good_phrase")
public class GoodPhrase extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notebook_id")
    private Long notebookId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source", length = 255)
    private String source;

    @Column(name = "theme", length = 50)
    private String theme;

    @Column(name = "emotion", length = 30)
    private String emotion;

    @Column(name = "usage_type", length = 50)
    private String usageType;

    @Column(name = "tags", length = 255)
    private String tags;

    @Column(name = "mastery_level", length = 20)
    private String masteryLevel = "不熟悉";

    @Column(name = "entry_method", length = 20)
    private String entryMethod = "text";

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "vector_id", length = 100)
    private String vectorId;

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @PrePersist
    void onCreate() {
        if (this.createdTime == null) {
            this.createdTime = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getNotebookId() { return notebookId; }
    public void setNotebookId(Long notebookId) { this.notebookId = notebookId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }

    public String getUsageType() { return usageType; }
    public void setUsageType(String usageType) { this.usageType = usageType; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getMasteryLevel() { return masteryLevel; }
    public void setMasteryLevel(String masteryLevel) { this.masteryLevel = masteryLevel; }

    public String getEntryMethod() { return entryMethod; }
    public void setEntryMethod(String entryMethod) { this.entryMethod = entryMethod; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getVectorId() { return vectorId; }
    public void setVectorId(String vectorId) { this.vectorId = vectorId; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
