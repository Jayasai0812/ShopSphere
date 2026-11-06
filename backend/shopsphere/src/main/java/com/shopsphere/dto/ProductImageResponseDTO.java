package com.shopsphere.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata of one product image as exposed through the API. The binary is
 * never returned — clients fetch the file itself from {@code imageUrl}.
 */
public record ProductImageResponseDTO(
                Long id,
                String imageUrl,
                int sortOrder,
                @JsonProperty("isPrimary") boolean primaryImage) {
}
