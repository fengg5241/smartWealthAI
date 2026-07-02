package com.smartwealth.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notebook")
public class Notebook extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "notebook_type", length = 20)
    private String notebookType = "mistake";

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getNotebookType() { return notebookType; }
    public void setNotebookType(String notebookType) { this.notebookType = notebookType; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
