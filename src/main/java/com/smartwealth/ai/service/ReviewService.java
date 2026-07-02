package com.smartwealth.ai.service;

import com.smartwealth.ai.domain.ReviewSchedule;
import com.smartwealth.ai.repository.MistakeQuestionRepository;
import com.smartwealth.ai.repository.ReviewScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReviewService {

    private final ReviewScheduleRepository scheduleRepo;
    private final MistakeQuestionRepository mistakeRepo;

    public ReviewService(ReviewScheduleRepository scheduleRepo, MistakeQuestionRepository mistakeRepo) {
        this.scheduleRepo = scheduleRepo;
        this.mistakeRepo = mistakeRepo;
    }

    /**
     * Create a review schedule for a mistake question (called after mistake is created).
     */
    @Transactional
    public ReviewSchedule scheduleForReview(String tenantId, Long mistakeId) {
        ReviewSchedule rs = new ReviewSchedule();
        rs.setTenantId(tenantId);
        rs.setMistakeId(mistakeId);
        rs.setReviewStage(1);
        rs.setEaseFactor(2.5);
        rs.setIntervalDays(1);
        rs.setNextReviewDate(LocalDate.now().plusDays(1)); // review tomorrow
        return scheduleRepo.save(rs);
    }

    /**
     * Submit a review result using SM-2 algorithm.
     * quality: 0 (again / forgot), 1 (hard / 不熟练), 2 (good / 掌握了)
     */
    @Transactional
    public ReviewResult submitReview(String tenantId, Long mistakeId, int quality) {
        ReviewSchedule rs = scheduleRepo.findByTenantIdAndMistakeId(tenantId, mistakeId).orElse(null);
        if (rs == null) {
            // First review — create schedule
            rs = scheduleForReview(tenantId, mistakeId);
        }

        // SM-2 algorithm
        double ef = rs.getEaseFactor();
        int interval = rs.getIntervalDays();
        int stage = rs.getReviewStage();

        if (quality < 2) {
            // Forgot or hard — reset
            stage = 1;
            interval = 1;
            ef = Math.max(1.3, ef - 0.2);
        } else {
            // Good — advance
            stage = Math.min(stage + 1, 7);
            if (stage == 2) {
                interval = 1;
            } else if (stage == 3) {
                interval = 3;
            } else {
                interval = (int) Math.round(interval * ef);
            }
            ef = ef + (0.1 - (3 - quality) * (0.08 + (3 - quality) * 0.02));
            ef = Math.max(1.3, ef);
        }

        rs.setReviewStage(stage);
        rs.setIntervalDays(interval);
        rs.setEaseFactor(Math.round(ef * 10.0) / 10.0);
        rs.setNextReviewDate(LocalDate.now().plusDays(interval));
        rs.setLastReviewed(LocalDateTime.now());
        scheduleRepo.save(rs);

        // Update mistake mastery level
        String mastery;
        if (quality == 2 && stage >= 3) mastery = "掌握";
        else if (quality >= 1) mastery = "一般";
        else mastery = "不熟悉";

        mistakeRepo.findById(mistakeId).ifPresent(m -> {
            m.setMasteryLevel(mastery);
            mistakeRepo.save(m);
        });

        long dueTomorrow = scheduleRepo.countDueReviews(tenantId, LocalDate.now().plusDays(1));
        return new ReviewResult(stage, interval, ef, rs.getNextReviewDate().toString(), mastery, dueTomorrow);
    }

    /**
     * Get all due reviews for a tenant (today and past-due).
     */
    public List<ReviewCard> getDueReviews(String tenantId) {
        List<ReviewSchedule> due = scheduleRepo.findDueReviews(tenantId, LocalDate.now());
        List<ReviewCard> cards = new ArrayList<>();
        for (ReviewSchedule rs : due) {
            mistakeRepo.findByIdAndTenantId(rs.getMistakeId(), tenantId).ifPresent(m -> {
                cards.add(new ReviewCard(rs.getId(), m.getId(), m.getContent(),
                        m.getCorrectAnswer(), m.getSubject(), m.getQuestionType(),
                        m.getMasteryLevel(), rs.getReviewStage(), rs.getNextReviewDate().toString()));
            });
        }
        return cards;
    }

    /**
     * Get count of due reviews today.
     */
    public long getDueCount(String tenantId) {
        return scheduleRepo.countDueReviews(tenantId, LocalDate.now());
    }

    /**
     * Get upcoming review stats for the next N days.
     */
    public Map<String, Long> getUpcomingStats(String tenantId, int days) {
        Map<String, Long> stats = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            LocalDate date = today.plusDays(i);
            stats.put(date.toString(), scheduleRepo.countDueReviews(tenantId, date));
        }
        return stats;
    }

    @Transactional
    public void removeSchedule(String tenantId, Long mistakeId) {
        scheduleRepo.findByTenantIdAndMistakeId(tenantId, mistakeId)
                .ifPresent(scheduleRepo::delete);
    }

    // ==================== Record types ====================

    public record ReviewResult(int stage, int intervalDays, double easeFactor,
                                String nextReviewDate, String masteryLevel, long dueTomorrow) {}
    public record ReviewCard(Long scheduleId, Long mistakeId, String content, String correctAnswer,
                              String subject, String questionType, String masteryLevel,
                              int reviewStage, String nextReviewDate) {}
}
