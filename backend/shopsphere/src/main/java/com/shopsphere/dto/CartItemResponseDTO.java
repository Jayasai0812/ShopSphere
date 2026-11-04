package com.shopsphere.dto;

import java.math.BigDecimal;

public record CartItemResponseDTO(
        Long itemId,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {}
