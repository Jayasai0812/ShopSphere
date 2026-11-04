package com.shopsphere.dto;

import jakarta.validation.constraints.NotNull;

public record CheckoutRequestDTO(

        @NotNull
        Long addressId
) {}
