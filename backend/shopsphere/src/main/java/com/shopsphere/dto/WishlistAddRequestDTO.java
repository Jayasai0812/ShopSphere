package com.shopsphere.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for POST /api/v1/users/me/wishlist/items (add a product to
 * the logged-in user's wishlist).
 *
 * Only the product id is supplied; identity and ownership are resolved
 * server-side via CurrentUserProvider — no user ID is accepted from the
 * request. Duplicate additions return 409 CONFLICT (not an error page — the
 * client is told the product is already saved).
 */
public record WishlistAddRequestDTO(

                @NotNull(message = "Product id is required")
                @Positive(message = "Product id must be positive")
                Long productId) {
}
