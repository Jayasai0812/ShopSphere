package com.shopsphere.repository;

import com.shopsphere.entity.Review;
import com.shopsphere.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** The author's one review of a product (any status). */
    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    /**
     * Public review list for a product: APPROVED only, newest first.
     * Pagination keeps large products cheap.
     */
    Page<Review> findByProductIdAndStatusOrderByIdDesc(Long productId, ReviewStatus status, Pageable pageable);

    /**
     * One grouped query for the rating summaries of many products — the
     * product endpoints call this once instead of per-product N+1.
     */
    @Query("""
            SELECT r.product.id AS productId,
                   AVG(r.rating) AS averageRating,
                   COUNT(r.id) AS reviewCount
            FROM Review r
            WHERE r.product.id IN :productIds AND r.status = com.shopsphere.entity.ReviewStatus.APPROVED
            GROUP BY r.product.id
            """)
    List<RatingSummaryProjection> summarizeByProductIds(@Param("productIds") List<Long> productIds);

    /** Single-product variant used by getProductById. */
    @Query("""
            SELECT AVG(r.rating) AS averageRating,
                   COUNT(r.id) AS reviewCount
            FROM Review r
            WHERE r.product.id = :productId AND r.status = com.shopsphere.entity.ReviewStatus.APPROVED
            """)
    RatingSummaryProjection summarizeByProductId(@Param("productId") Long productId);

    /** Admin moderation queue: newest first, all statuses. */
    Page<Review> findByStatusOrderByIdDesc(ReviewStatus status, Pageable pageable);

    /** Authors' names for a page of reviews, avoiding N lazy loads. */
    @Query("""
            SELECT r.id AS reviewId, u.name AS authorName
            FROM Review r JOIN User u ON u.id = r.user.id
            WHERE r.id IN :reviewIds
            """)
    List<ReviewAuthorProjection> findAuthorNames(@Param("reviewIds") List<Long> reviewIds);

    interface RatingSummaryProjection {
        Long getProductId();

        Double getAverageRating();

        Long getReviewCount();
    }

    interface ReviewAuthorProjection {
        Long getReviewId();

        String getAuthorName();
    }
}
