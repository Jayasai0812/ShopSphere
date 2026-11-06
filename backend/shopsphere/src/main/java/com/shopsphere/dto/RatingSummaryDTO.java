package com.shopsphere.dto;

/**
 * Rating summary carried on every product response (TASK-018).
 *
 * Counted over APPROVED reviews only, computed in the database via a
 * single grouped query — not per-product N+1. A product with no approved
 * reviews has averageRating = null and reviewCount = 0.
 */
public record RatingSummaryDTO(
                Long productId,
                Double averageRating,
                long reviewCount) {

        public static RatingSummaryDTO empty(Long productId) {
                return new RatingSummaryDTO(productId, null, 0L);
        }
}
