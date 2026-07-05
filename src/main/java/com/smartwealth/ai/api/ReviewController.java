package com.smartwealth.ai.api;

import com.smartwealth.ai.service.ReviewService;
import com.smartwealth.ai.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/due")
    public ResponseEntity<Map<String, Object>> getDue() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        List<ReviewService.ReviewCard> cards = reviewService.getDueReviews(tenantId);
        long count = reviewService.getDueCount(tenantId);
        Map<String, Long> upcoming = reviewService.getUpcomingStats(tenantId, 7);

        List<ReviewService.PhraseReviewCard> phraseCards = reviewService.getDuePhraseReviews(tenantId);

        return ResponseEntity.ok(Map.of(
                "cards", cards,
                "dueCount", count,
                "upcoming", upcoming,
                "phraseCards", phraseCards));
    }

    @PostMapping("/{mistakeId}")
    public ResponseEntity<Map<String, Object>> submit(@PathVariable Long mistakeId,
                                                       @RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        int quality = body.get("quality") instanceof Number n ? n.intValue() : 0;
        if (quality < 0 || quality > 2) return bad("quality must be 0 (again), 1 (hard), or 2 (good)");

        ReviewService.ReviewResult result = reviewService.submitReview(tenantId, mistakeId, quality);

        return ResponseEntity.ok(Map.of(
                "stage", result.stage(),
                "intervalDays", result.intervalDays(),
                "easeFactor", result.easeFactor(),
                "nextReviewDate", result.nextReviewDate(),
                "masteryLevel", result.masteryLevel(),
                "dueTomorrow", result.dueTomorrow()));
    }

    @PostMapping("/phrase/{phraseId}")
    public ResponseEntity<Map<String, Object>> submitPhrase(@PathVariable Long phraseId,
                                                             @RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return bad("Missing X-Tenant-ID header");

        int quality = body.get("quality") instanceof Number n ? n.intValue() : 0;
        if (quality < 0 || quality > 2) return bad("quality must be 0 (again), 1 (hard), or 2 (good)");

        ReviewService.ReviewResult result = reviewService.submitPhraseReview(tenantId, phraseId, quality);

        return ResponseEntity.ok(Map.of(
                "stage", result.stage(),
                "intervalDays", result.intervalDays(),
                "easeFactor", result.easeFactor(),
                "nextReviewDate", result.nextReviewDate(),
                "masteryLevel", result.masteryLevel(),
                "dueTomorrow", result.dueTomorrow()));
    }

    private static ResponseEntity<Map<String, Object>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }
}
