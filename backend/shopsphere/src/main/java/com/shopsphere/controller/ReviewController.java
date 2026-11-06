package com.shopsphere.controller;

import com.shopsphere.dto.ReviewRequestDTO;
import com.shopsphere.dto.ReviewResponseDTO;
import com.shopsphere.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing review endpoints (TASK-018).
 *
 * All routes resolve the reviewer from the JWT security context — no user
 * ID is accepted from the request, and users can only read, update or
 * delete their OWN review (someone else's is 404).
 *
 * Moderation queue lives under /api/v1/admin/reviews (ADMIN only).
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ReviewController {

        private final ReviewService reviewService;

        public ReviewController(ReviewService reviewService) {
                this.reviewService = reviewService;
        }

        /** Lists APPROVED reviews for the product, newest first. */
        @GetMapping
        public ResponseEntity<Page<ReviewResponseDTO>> listReviews(
                        @PathVariable Long productId, Pageable pageable) {
                return ResponseEntity.ok(reviewService.getPublicReviews(productId, pageable));
        }

        /** Submits the logged-in user's review of a purchased product. */
        @PostMapping
        public ResponseEntity<ReviewResponseDTO> createReview(
                        @PathVariable Long productId,
                        @Valid @RequestBody ReviewRequestDTO request) {
                ReviewResponseDTO created = reviewService.createReview(productId, request);
                return ResponseEntity.created(
                                java.net.URI.create("/api/v1/products/" + productId + "/reviews/me"))
                                .body(created);
        }

        /** Returns the logged-in user's own review of the product. */
        @GetMapping("/me")
        public ResponseEntity<ReviewResponseDTO> getMyReview(@PathVariable Long productId) {
                return ResponseEntity.ok(reviewService.getMyReview(productId));
        }

        /** Updates the logged-in user's own review (re-enters moderation). */
        @PutMapping("/me")
        public ResponseEntity<ReviewResponseDTO> updateMyReview(
                        @PathVariable Long productId,
                        @Valid @RequestBody ReviewRequestDTO request) {
                return ResponseEntity.ok(reviewService.updateMyReview(productId, request));
        }

        /** Deletes the logged-in user's own review. Returns 204. */
        @DeleteMapping("/me")
        public ResponseEntity<Void> deleteMyReview(@PathVariable Long productId) {
                reviewService.deleteMyReview(productId);
                return ResponseEntity.noContent().build();
        }
}
