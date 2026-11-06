package com.shopsphere.dto;

import java.util.List;

/**
 * Response body for GET /api/v1/users/me/wishlist.
 *
 * The wishlist is created eagerly at registration (AuthService persists a
 * Cart and a Wishlist alongside the user), so {@code wishlistId} is always
 * present and the items list is empty for a user who has not saved
 * anything yet. Items are returned oldest-saved first (repository
 * ordering), making the list stable between calls.
 */
public record WishlistResponseDTO(
        Long wishlistId,
        List<WishlistItemResponseDTO> items) {
}
