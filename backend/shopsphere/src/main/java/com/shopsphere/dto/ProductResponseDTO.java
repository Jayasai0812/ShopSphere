package com.shopsphere.dto;

import java.math.BigDecimal;

/**
 * Public product representation.
 *
 * {@code rating} (TASK-018) carries the database-computed average and
 * count of APPROVED reviews — null/0 when the product has none. It is
 * populated by ProductService from ReviewService's grouped summaries.
 *
 * {@code images} (image-management phase) lists the product's image
 * metadata in sort order — the primary image first when one exists.
 */
public record ProductResponseDTO(
                Long id,
                String name,
                String sku,
                BigDecimal price,
                int stockQuantity,
                Long categoryId,
                String status,
                RatingSummaryDTO rating,
                java.util.List<ProductImageResponseDTO> images) {

        /** Backward-compatible factory for callers that have no image data. */
        public static ProductResponseDTO withoutImages(
                        Long id, String name, String sku, BigDecimal price,
                        int stockQuantity, Long categoryId, String status,
                        RatingSummaryDTO rating) {
                return new ProductResponseDTO(id, name, sku, price, stockQuantity,
                                categoryId, status, rating, java.util.List.of());
        }
}
