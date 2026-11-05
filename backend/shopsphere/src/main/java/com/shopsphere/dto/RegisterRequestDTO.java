package com.shopsphere.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for POST /api/v1/auth/register.
 *
 * Note: there is intentionally NO role field here. Roles are assigned
 * server-side (CUSTOMER), so a client can never self-assign ADMIN.
 * The raw password is carried only in memory for hashing and is never
 * exposed by any response DTO.
 */
public record RegisterRequestDTO(

                @NotBlank(message = "Name is required") @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters") String name,

                @NotBlank(message = "Email is required") @Email(message = "Email must be a valid address") @Size(max = 255, message = "Email must not exceed 255 characters") String email,

                @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "Password must contain at least one uppercase letter, one lowercase letter and one digit") String password,

                @Pattern(regexp = "^$|^\\+?[0-9\\-\\s]{7,20}$", message = "Phone must be a valid phone number") String phone) {
}
