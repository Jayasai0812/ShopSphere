package com.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for PATCH /api/v1/users/me/password.
 *
 * The current password is required as proof of identity — the new
 * password alone must never be enough to take over an open session.
 * New-password rules mirror registration so the two flows can never
 * disagree on what is valid.
 *
 * A confirmNewPassword field is deliberately not included: matching
 * confirmation is a frontend UX concern, and enforcing it server-side
 * only creates a second validation surface to keep in sync.
 */
public record ChangePasswordRequestDTO(

                @NotBlank(message = "Current password is required") String currentPassword,

                @NotBlank(message = "New password is required") @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).+$", message = "Password must contain uppercase, lowercase and a digit") String newPassword) {
}
