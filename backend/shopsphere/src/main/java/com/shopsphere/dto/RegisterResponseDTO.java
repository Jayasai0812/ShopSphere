package com.shopsphere.dto;

import java.util.List;

/**
 * Response payload for a successful registration. Carries only
 * non-sensitive user data — never the password or its hash.
 */
public record RegisterResponseDTO(
        Long id,
        String name,
        String email,
        String phone,
        List<String> roles
) {}
