package com.shopsphere.controller;

import com.shopsphere.dto.ReviewResponseDTO;
import com.shopsphere.entity.ReviewStatus;
import com.shopsphere.service.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin review-moderation endpoints (TASK-018). Secured to ROLE_ADMIN by
 * the request matchers added to SecurityConfig for /api/v1/admin/**.
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

        private final ReviewService reviewService;

        public AdminReviewController(ReviewService reviewService) {
                this.reviewService = reviewService;
        }

        /** Moderation queue: all reviews, optionally filtered by status. */
        @GetMapping
        public ResponseEntity<Page<ReviewResponseDTO>> listReviews(
                        @RequestParam(required = false) ReviewStatus status, Pageable pageable) {
                return ResponseEntity.ok(reviewService.getAllReviews(status, pageable));
        }

        /** Approves a review — it becomes public and counts in averages. */
        @PatchMapping("/{reviewId}/approve")
        public ResponseEntity<ReviewResponseDTO> approve(@PathVariable Long reviewId) {
                return ResponseEntity.ok(reviewService.approveReview(reviewId));
        }

        /** Rejects a review — hidden from the public but retained. */
        @PatchMapping("/{reviewId}/reject")
        public ResponseEntity<ReviewResponseDTO> reject(@PathVariable Long reviewId) {
                return ResponseEntity.ok(reviewService.rejectReview(reviewId));
        }
}
