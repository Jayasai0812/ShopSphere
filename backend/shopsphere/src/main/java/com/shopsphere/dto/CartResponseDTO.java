package com.shopsphere.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponseDTO(
        Long cartId,
        List<CartItemResponseDTO> items,
        int totalItems,
        BigDecimal totalAmount
) {}
