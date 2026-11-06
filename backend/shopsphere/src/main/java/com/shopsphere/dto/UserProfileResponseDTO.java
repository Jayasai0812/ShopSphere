package com.shopsphere.dto;

import java.util.List;

/**
 * The logged-in user's own profile, served by GET /api/v1/users/me.
 *
 * Carries only non-sensitive data — never the password or its hash.
 * Roles are included for UI convenience and are always read from the
 * live database, never echoed from the token claims.
 */
public record UserProfileResponseDTO(
                Long id,
                String name,
                String email,
                String phone,
                List<String> roles) {
}
