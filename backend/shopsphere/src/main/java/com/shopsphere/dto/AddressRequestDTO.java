package com.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/addresses (address creation).
 *
 * All identity and ownership is resolved server-side: the address is
 * always attached to the user from the JWT security context — no user ID
 * is accepted from the request.
 *
 * Postal code is restricted to 5-10 digits, which covers the formats
 * used by our initial markets while staying strict enough to catch
 * malformed input.
 */
public record AddressRequestDTO(

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9\\-\\s]{7,20}$", message = "Phone must be a valid phone number")
        String phone,

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
        String addressLine1,

        @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
        String addressLine2,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,

        @NotBlank(message = "Postal code is required")
        @Pattern(regexp = "^[0-9]{5,10}$", message = "Postal code must be 5-10 digits")
        String postalCode,

        @NotBlank(message = "Country is required")
        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        @Pattern(regexp = "HOME|WORK|OTHER", message = "Address type must be HOME, WORK or OTHER")
        String addressType,

        Boolean isDefault) {
}
