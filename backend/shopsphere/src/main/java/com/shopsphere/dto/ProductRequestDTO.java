package com.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductRequestDTO(

        @NotBlank
        String name,

        String description,

        @NotBlank
        String sku,

        @NotNull @Positive
        BigDecimal price,

        @PositiveOrZero
        int stockQuantity,

        @NotNull
        Long categoryId,

        @NotNull
        Long sellerId
) {}
