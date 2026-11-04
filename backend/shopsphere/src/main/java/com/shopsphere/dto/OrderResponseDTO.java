package com.shopsphere.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponseDTO(
        Long orderId,
        String status,
        BigDecimal totalAmount,
        String shippingAddressLine1,
        String shippingCity,
        String shippingState,
        String shippingPincode,
        String shippingCountry,
        List<OrderItemResponseDTO> items
) {}
