package com.shopsphere.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CheckoutRequestDTO(

        @NotNull
        @Positive
        Long addressId
) {}
