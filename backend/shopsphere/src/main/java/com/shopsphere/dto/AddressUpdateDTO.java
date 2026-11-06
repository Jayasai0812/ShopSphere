package com.shopsphere.dto;

import com.shopsphere.entity.AddressType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for PATCH /api/v1/users/me/addresses/{id} (partial update).
 *
 * Every field is optional: only the fields present in the JSON are
 * updated, so the same DTO serves both "edit the whole address" and
 * "just flip isDefault" use cases. Null means "leave unchanged", and an
 * explicit blank string can be supplied for addressLine2 to clear it.
 *
 * Field constraints are identical to AddressRequestDTO so a full update
 * payload validates the same way a create payload does.
 */
public record AddressUpdateDTO(

                @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters") String fullName,

                @Pattern(regexp = "^\\+?[0-9\\-\\s]{7,20}$", message = "Phone must be a valid phone number") String phone,

                @Size(min = 1, max = 255, message = "Address line 1 must be between 1 and 255 characters") String addressLine1,

                @Size(max = 255, message = "Address line 2 must not exceed 255 characters") String addressLine2,

                @Size(min = 1, max = 100, message = "City must be between 1 and 100 characters") String city,

                @Size(min = 1, max = 100, message = "State must be between 1 and 100 characters") String state,

                @Pattern(regexp = "^[0-9]{5,10}$", message = "Postal code must be 5-10 digits") String postalCode,

                @Size(min = 1, max = 100, message = "Country must be between 1 and 100 characters") String country,

                AddressType addressType,

                Boolean isDefault) {
}
