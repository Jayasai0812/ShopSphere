package com.shopsphere.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CartItemUpdateDTO(

        @NotNull
        @Positive
        Integer quantity
) {}
