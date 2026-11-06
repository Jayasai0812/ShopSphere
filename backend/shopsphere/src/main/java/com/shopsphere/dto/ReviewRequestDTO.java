package com.shopsphere.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/products/{productId}/reviews and
 * PUT /api/v1/products/{productId}/reviews/me (create or update the
 * logged-in user's own review).
 *
 * Purchase eligibility and the one-review-per-product rule are verified in
 * the service against real order history — the request carries no user id
 * and no "proof of purchase"; those cannot be trusted from a client.
 *
 * rating is validated as an integer 1..5 (scale 0 rejects "3.5").
 * comment is optional; blank-as-null is normalized in the service.
 */
public record ReviewRequestDTO(

                @DecimalMin(value = "1", message = "Rating must be at least 1")
                @DecimalMax(value = "5", message = "Rating must be at most 5")
                @Digits(integer = 1, fraction = 0, message = "Rating must be a whole number from 1 to 5")
                Integer rating,

                @Size(max = 2000, message = "Comment must not exceed 2000 characters")
                String comment) {
}
