package com.shopsphere.dto;

import com.shopsphere.entity.AddressType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for PATCH /api/v1/users/me/addresses/{id} — a PARTIAL
 * update, not a full replacement: fields absent from the JSON keep their
 * current value.
 *
 * Null-handling rule (simple, deliberately coarse): a field that is
 * omitted from the JSON or explicitly sent as null means "leave
 * unchanged". Jackson does not let a plain record distinguish those two
 * cases, so they are treated identically. The one exception is
 * addressLine2: sending an empty string ("") clears the field, since a
 * nullable second line has a meaningful "clear" action.
 *
 * Whitespace-only values ("   ") are rejected for every non-nullable text
 * field, not merely size-checked — @Size counts spaces, so it alone cannot
 * catch them. The pattern requires at least one non-whitespace character.
 *
 * Field constraints are identical to AddressRequestDTO so a full update
 * payload validates the same way a create payload does.
 */
public record AddressUpdateDTO(

                @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
                @Pattern(regexp = ".*\\S.*", message = "Full name must not be blank")
                String fullName,

                @Pattern(regexp = "^\\+?[0-9](?:[0-9\\-\\s]{5,18})[0-9]$",
                                message = "Phone must be a valid phone number")
                String phone,

                @Size(min = 1, max = 255, message = "Address line 1 must be between 1 and 255 characters")
                @Pattern(regexp = ".*\\S.*", message = "Address line 1 must not be blank")
                String addressLine1,

                @Size(max = 255, message = "Address line 2 must not exceed 255 characters") String addressLine2,

                @Size(min = 1, max = 100, message = "City must be between 1 and 100 characters")
                @Pattern(regexp = ".*\\S.*", message = "City must not be blank")
                String city,

                @Size(min = 1, max = 100, message = "State must be between 1 and 100 characters")
                @Pattern(regexp = ".*\\S.*", message = "State must not be blank")
                String state,

                @Pattern(regexp = "^[0-9]{5,10}$", message = "Postal code must be 5-10 digits") String postalCode,

                @Size(min = 1, max = 100, message = "Country must be between 1 and 100 characters")
                @Pattern(regexp = ".*\\S.*", message = "Country must not be blank")
                String country,

                AddressType addressType,

                Boolean isDefault) {
}
