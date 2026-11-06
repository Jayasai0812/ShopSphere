package com.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/v1/users/me.
 *
 * Email is intentionally absent — changing the login identifier is a
 * separate, verified flow, not a casual profile edit. The user whose
 * profile is updated is always resolved from the JWT security context;
 * no user ID is accepted from the request.
 *
 * Phone is nullable so users can clear it; whitespace-only input is
 * normalized to null in the service.
 */
public record UpdateProfileRequestDTO(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 20, message = "Phone must not exceed 20 characters")
        @Pattern(regexp = "^[0-9+\\-() ]*$", message = "Phone contains invalid characters")
        String phone) {
}
