package com.shopsphere.dto;

import java.math.BigDecimal;

public record ProductResponseDTO(
        Long id,
        String name,
        String sku,
        BigDecimal price,
        int stockQuantity,
        Long categoryId,
        String status
) {}
