package com.shopsphere.dto;

import java.math.BigDecimal;

/**
 * One saved product inside the wishlist response. Carries the catalog
 * snapshot the frontend needs to render a wishlist card: name, sku,
 * current price and availability. Price is read live from the product at
 * request time — no snapshot is stored, unlike order line items.
 *
 * {@code available} is a computed convenience flag (active && stock > 0)
 * so the UI can grey out items that can no longer be bought without a
 * second catalog call.
 */
public record WishlistItemResponseDTO(
                Long itemId,
                Long productId,
                String productName,
                String sku,
                BigDecimal price,
                boolean available) {
}
