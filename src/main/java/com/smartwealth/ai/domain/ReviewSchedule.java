package com.smartwealth.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_schedule")
public class ReviewSchedule extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mistake_id")
    private Long mistakeId;

    @Column(name = "phrase_id")
    private Long phraseId;

    @Column(name = "review_stage")
    private Integer reviewStage = 1;

    @Column(name = "ease_factor")
    private double easeFactor = 2.5;

    @Column(name = "interval_days")
    private Integer intervalDays = 1;

    @Column(name = "next_review_date", nullable = false)
    private LocalDate nextReviewDate;

    @Column(name = "last_reviewed")
    private LocalDateTime lastReviewed;

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

    public Long getMistakeId() { return mistakeId; }
    public void setMistakeId(Long mistakeId) { this.mistakeId = mistakeId; }

    public Long getPhraseId() { return phraseId; }
    public void setPhraseId(Long phraseId) { this.phraseId = phraseId; }

    public Integer getReviewStage() { return reviewStage; }
    public void setReviewStage(Integer reviewStage) { this.reviewStage = reviewStage; }

    public double getEaseFactor() { return easeFactor; }
    public void setEaseFactor(double easeFactor) { this.easeFactor = easeFactor; }

    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }

    public LocalDate getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(LocalDate nextReviewDate) { this.nextReviewDate = nextReviewDate; }

    public LocalDateTime getLastReviewed() { return lastReviewed; }
    public void setLastReviewed(LocalDateTime lastReviewed) { this.lastReviewed = lastReviewed; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
