package com.shopsphere.dto;

public record CategoryResponseDTO(
        Long id,
        String name,
        String description,
        boolean active
) {}
