package com.shopsphere.dto;

import java.math.BigDecimal;

/**
 * Public product representation.
 *
 * {@code rating} (TASK-018) carries the database-computed average and
 * count of APPROVED reviews — null/0 when the product has none. It is
 * populated by ProductService from ReviewService's grouped summaries.
 */
public record ProductResponseDTO(
        Long id,
        String name,
        String sku,
        BigDecimal price,
        int stockQuantity,
        Long categoryId,
        String status,
        RatingSummaryDTO rating
) {}
