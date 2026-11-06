package com.shopsphere.service;

import com.shopsphere.common.CurrentUserProvider;
import com.shopsphere.dto.RatingSummaryDTO;
import com.shopsphere.dto.ReviewRequestDTO;
import com.shopsphere.dto.ReviewResponseDTO;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.Review;
import com.shopsphere.entity.ReviewStatus;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.ReviewRepository;
import com.shopsphere.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Product reviews and ratings for TASK-018.
 *
 * Identity and eligibility rules:
 * - The reviewer is ALWAYS resolved from the JWT security context via
 * {@link CurrentUserProvider}; no user id is accepted from a request.
 * - A review can only be submitted for a product the user actually
 * purchased, verified against order history: at least one order item in
 * a DELIVERED order belonging to the caller. Anything else is 403 — the
 * client's claims can never substitute for order history.
 * - One review per (user, product): an exists-check returns a clean 409;
 * the uk_reviews_user_product DB constraint remains the backstop.
 *
 * Moderation:
 * - New and edited reviews start in PENDING; only APPROVED reviews appear
 * on the public product endpoints and in rating averages. Admins list,
 * approve and reject via the /api/v1/admin/reviews endpoints (secured
 * by ROLE_ADMIN in SecurityConfig).
 * - Users can update or delete their OWN review (404 for anyone else's),
 * and edits return the review to PENDING for re-moderation.
 *
 * Ratings:
 * - Summaries are computed by the database in grouped queries
 * (summarizeByProductIds / summarizeByProductId) over APPROVED reviews
 * only — no per-product N+1.
 */
@Service
public class ReviewService {

        private final ReviewRepository reviewRepository;
        private final OrderItemRepository orderItemRepository;
        private final ProductRepository productRepository;
        private final UserRepository userRepository;
        private final CurrentUserProvider currentUserProvider;

        private static final int PUBLIC_PAGE_SIZE = 20;

        public ReviewService(ReviewRepository reviewRepository,
                        OrderItemRepository orderItemRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository,
                        CurrentUserProvider currentUserProvider) {
                this.reviewRepository = reviewRepository;
                this.orderItemRepository = orderItemRepository;
                this.productRepository = productRepository;
                this.userRepository = userRepository;
                this.currentUserProvider = currentUserProvider;
        }

        // ── customer endpoints ─────────────────────────────────────────────

        /** Submits a new review for a purchased product. */
        @Transactional
        public ReviewResponseDTO createReview(Long productId, ReviewRequestDTO request) {
                Long userId = currentUserId();

                Product product = productRepository.findById(productId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Product not found with id: " + productId));

                if (!orderItemRepository.existsDeliveredPurchase(userId, productId)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                        "You can only review products you have purchased");
                }

                if (reviewRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "You have already reviewed this product");
                }

                Review review = new Review();
                review.setUser(userRepository.getReferenceById(userId));
                review.setProduct(product);
                review.setRating(requireRating(request));
                review.setComment(normalizeComment(request.comment()));
                review.setStatus(ReviewStatus.PENDING);
                try {
                        return toDto(reviewRepository.saveAndFlush(review), null);
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        // Two concurrent submissions both passed the exists-check;
                        // uk_reviews_user_product rejected the second insert.
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "You have already reviewed this product");
                }
        }

        /** Returns the caller's own review of a product, or 404. */
        @Transactional(readOnly = true)
        public ReviewResponseDTO getMyReview(Long productId) {
                Review review = reviewRepository
                                .findByUserIdAndProductId(currentUserId(), productId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "You have not reviewed this product"));
                return toDto(review, null);
        }

        /** Updates the caller's own review; returns it to PENDING. */
        @Transactional
        public ReviewResponseDTO updateMyReview(Long productId, ReviewRequestDTO request) {
                Review review = reviewRepository
                                .findByUserIdAndProductId(currentUserId(), productId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "You have not reviewed this product"));

                review.setRating(requireRating(request));
                review.setComment(normalizeComment(request.comment()));
                review.setStatus(ReviewStatus.PENDING);
                return toDto(reviewRepository.save(review), null);
        }

        /** Extracts a valid 1..5 integer rating; a missing (null) rating is
         *  a 400, not an NPE — bean validation already rejects out-of-range
         *  and fractional values on the BigDecimal field. */
        private int requireRating(ReviewRequestDTO request) {
                if (request.rating() == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Rating is required");
                }
                return request.ratingValue();
        }

        /** Deletes the caller's own review; anyone else's is 404. */
        @Transactional
        public void deleteMyReview(Long productId) {
                Review review = reviewRepository
                                .findByUserIdAndProductId(currentUserId(), productId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "You have not reviewed this product"));
                reviewRepository.delete(review);
        }

        /** Public approved reviews for a product, newest first. The product
         *  must exist — a review list is meaningless for an unknown product. */
        @Transactional(readOnly = true)
        public Page<ReviewResponseDTO> getPublicReviews(Long productId, Pageable pageable) {
                if (!productRepository.existsById(productId)) {
                        throw new ResourceNotFoundException(
                                        "Product not found with id: " + productId);
                }
                Page<Review> page = reviewRepository.findByProductIdAndStatusOrderByIdDesc(
                                productId, ReviewStatus.APPROVED,
                                PageRequest.of(pageable.getPageNumber(), PUBLIC_PAGE_SIZE,
                                                pageable.getSort()));
                return toAuthorResolvedPage(page);
        }

        // ── admin moderation endpoints ─────────────────────────────────────

        /** All reviews regardless of status (moderation queue), newest first. */
        @Transactional(readOnly = true)
        public Page<ReviewResponseDTO> getAllReviews(ReviewStatus status, Pageable pageable) {
                Pageable sorted = pageable.getSort().isSorted() ? pageable
                                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                                org.springframework.data.domain.Sort.by(
                                                                org.springframework.data.domain.Sort.Direction.DESC,
                                                                "id"));
                Page<Review> page = status == null
                                ? reviewRepository.findAll(sorted)
                                : reviewRepository.findByStatusOrderByIdDesc(status, sorted);
                return toAuthorResolvedPage(page);
        }

        @Transactional
        public ReviewResponseDTO approveReview(Long reviewId) {
                return setStatus(reviewId, ReviewStatus.APPROVED);
        }

        @Transactional
        public ReviewResponseDTO rejectReview(Long reviewId) {
                return setStatus(reviewId, ReviewStatus.REJECTED);
        }

        // ── rating summaries used by the product endpoints ─────────────────

        /** Average + count for one product (APPROVED reviews only). */
        public RatingSummaryDTO summarize(Long productId) {
                ReviewRepository.RatingSummaryProjection row = reviewRepository.summarizeByProductId(productId);
                if (row == null || row.getReviewCount() == null || row.getReviewCount() == 0) {
                        return RatingSummaryDTO.empty(productId);
                }
                return new RatingSummaryDTO(productId, row.getAverageRating(), row.getReviewCount());
        }

        /** Summaries for many products in ONE grouped query (no N+1). */
        public Map<Long, RatingSummaryDTO> summarizeAll(List<Long> productIds) {
                if (productIds.isEmpty()) {
                        return Map.of();
                }
                Map<Long, RatingSummaryDTO> byId = reviewRepository.summarizeByProductIds(productIds)
                                .stream()
                                .collect(Collectors.toMap(
                                                ReviewRepository.RatingSummaryProjection::getProductId,
                                                row -> new RatingSummaryDTO(
                                                                row.getProductId(),
                                                                row.getAverageRating(),
                                                                row.getReviewCount())));
                // Every requested product gets an entry — empty when no reviews.
                return productIds.stream().collect(Collectors.toMap(
                                Function.identity(),
                                id -> byId.getOrDefault(id, RatingSummaryDTO.empty(id))));
        }

        // ── helpers ────────────────────────────────────────────────────────

        private ReviewResponseDTO setStatus(Long reviewId, ReviewStatus status) {
                Review review = reviewRepository.findById(reviewId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Review not found with id: " + reviewId));
                review.setStatus(status);
                return toDto(reviewRepository.save(review), null);
        }

        private String normalizeComment(String comment) {
                if (comment == null || comment.isBlank()) {
                        return null;
                }
                return comment.trim();
        }

        private Page<ReviewResponseDTO> toAuthorResolvedPage(Page<Review> page) {
                List<Long> ids = page.getContent().stream().map(Review::getId).toList();
                Map<Long, String> authors = ids.isEmpty()
                                ? Map.of()
                                : reviewRepository.findAuthorNames(ids).stream()
                                                .collect(Collectors.toMap(
                                                                ReviewRepository.ReviewAuthorProjection::getReviewId,
                                                                ReviewRepository.ReviewAuthorProjection::getAuthorName));
                return page.map(review -> toDto(review, authors.get(review.getId())));
        }

        private ReviewResponseDTO toDto(Review review, String authorName) {
                String author = authorName;
                if (author == null) {
                        // Single-review paths: the user reference is already loaded
                        // or lazily resolvable within the transaction.
                        author = review.getUser().getName();
                }
                return new ReviewResponseDTO(
                                review.getId(),
                                review.getProduct().getId(),
                                author,
                                review.getRating(),
                                review.getComment(),
                                review.getStatus(),
                                review.getCreatedAt(),
                                review.getUpdatedAt());
        }

        private Long currentUserId() {
                return currentUserProvider.getCurrentUserId();
        }
}
