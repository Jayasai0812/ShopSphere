package com.shopsphere.dto;

import com.shopsphere.entity.AddressType;

/**
 * Response body for the address endpoints under /api/v1/users/me/addresses
 * (GET list, GET by id, POST create, PATCH update, DELETE).
 *
 * Mirrors the enriched Address entity exactly. Contains no sensitive
 * data — all fields are things the owner already knows. {@code isDefault}
 * is exposed so the frontend can render the default badge and pre-select
 * the shipping address at checkout.
 */
public record AddressResponseDTO(
                Long id,
                String fullName,
                String phone,
                String addressLine1,
                String addressLine2,
                String city,
                String state,
                String postalCode,
                String country,
                AddressType addressType,
                boolean isDefault) {
}
