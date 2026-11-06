package com.shopsphere.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for POST /api/v1/products/{productId}/reviews and
 * PUT /api/v1/products/{productId}/reviews/me (create or update the
 * logged-in user's own review).
 *
 * Purchase eligibility and the one-review-per-product rule are verified in
 * the service against real order history — the request carries no user id
 * and no "proof of purchase"; those cannot be trusted from a client.
 *
 * RATING REPRESENTATION (TASK-018 review fix B):
 * rating is deserialized as {@link BigDecimal} so the incoming JSON keeps
 * its decimal form. An {@code Integer} field would let Jackson coerce the
 * numeric 3.5 into 3 BEFORE bean validation runs, silently accepting a
 * fractional rating. With BigDecimal, @Digits(fraction = 0) sees the real
 * scale and rejects 3.5 with 400 while 1..5 whole numbers pass.
 * The service converts the validated value via ratingValue().
 */
public record ReviewRequestDTO(

                @DecimalMin(value = "1", message = "Rating must be at least 1")
                @DecimalMax(value = "5", message = "Rating must be at most 5")
                @Digits(integer = 1, fraction = 0, message = "Rating must be a whole number from 1 to 5")
                BigDecimal rating,

                @Size(max = 2000, message = "Comment must not exceed 2000 characters") String comment) {

        /** The validated integer rating (1..5). Null-safe: a missing rating
         *  is reported as a validation failure, not an NPE. */
        public int ratingValue() {
                if (rating == null) {
                        throw new IllegalArgumentException("Rating is required");
                }
                return rating.intValueExact();
        }
}
