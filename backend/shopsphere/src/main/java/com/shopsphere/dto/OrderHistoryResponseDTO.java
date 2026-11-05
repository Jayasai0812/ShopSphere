package com.shopsphere.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Summary for a single order in the customer's paginated order history.
 * Carries the same snapshot fields as the details response so that a
 * customer's historical order view stays stable when products or addresses
 * change later.
 */
public record OrderHistoryResponseDTO(
        Long orderId,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        List<OrderItemResponseDTO> items
) {}
