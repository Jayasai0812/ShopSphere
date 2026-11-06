package com.shopsphere.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for POST /api/v1/auth/login.
 *
 * Validation is intentionally looser than registration: no password
 * complexity rules here. Complexity is a registration concern; at login a
 * wrong-but-well-formed password must fail as 401, not as 400, and a legacy
 * password that no longer meets current complexity rules must still be able
 * to authenticate.
 */
public record LoginRequestDTO(

                @NotBlank(message = "Email is required")
                @Email(message = "Email must be a valid address")
                @Size(max = 255, message = "Email must not exceed 255 characters")
                String email,

                @NotBlank(message = "Password is required")
                @Size(max = 72, message = "Password must not exceed 72 characters")
                String password) {
}
