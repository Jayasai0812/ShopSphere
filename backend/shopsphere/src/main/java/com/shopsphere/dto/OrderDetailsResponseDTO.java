package com.shopsphere.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full details of a single customer order: order metadata, the shipping
 * address snapshot taken at checkout, and every item with its price and
 * product-name snapshot (products may change after the order is placed).
 */
public record OrderDetailsResponseDTO(
        Long orderId,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        String shippingAddressLine1,
        String shippingCity,
        String shippingState,
        String shippingPincode,
        String shippingCountry,
        List<OrderItemResponseDTO> items
) {}
