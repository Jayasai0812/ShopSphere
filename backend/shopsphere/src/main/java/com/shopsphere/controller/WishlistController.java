package com.shopsphere.controller;

import com.shopsphere.dto.WishlistAddRequestDTO;
import com.shopsphere.dto.WishlistItemResponseDTO;
import com.shopsphere.dto.WishlistResponseDTO;
import com.shopsphere.service.WishlistService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Self-service wishlist endpoints for the authenticated user (TASK-017).
 *
 * All routes operate exclusively on the identity from the JWT security
 * context — there is no user ID path variable and no user ID in any request
 * body. Item ownership is enforced in {@link WishlistService}: touching an
 * item that belongs to another user returns 404, never 403, so the endpoint
 * does not reveal the item's existence.
 */
@RestController
@RequestMapping("/api/v1/users/me/wishlist")
public class WishlistController {

        private final WishlistService wishlistService;

        public WishlistController(WishlistService wishlistService) {
                this.wishlistService = wishlistService;
        }

        /** Lists the logged-in user's saved products. */
        @GetMapping
        public ResponseEntity<WishlistResponseDTO> getWishlist() {
                return ResponseEntity.ok(wishlistService.getWishlist());
        }

        /**
         * Saves a product to the logged-in user's wishlist. Returns 201 +
         * Location on success and 409 if the product is already saved (or
         * is no longer available for sale).
         */
        @PostMapping("/items")
        public ResponseEntity<WishlistItemResponseDTO> addItem(
                        @Valid @RequestBody WishlistAddRequestDTO request) {
                WishlistItemResponseDTO created = wishlistService.addItem(request);
                return ResponseEntity
                                .created(URI.create("/api/v1/users/me/wishlist/items/" + created.itemId()))
                                .body(created);
        }

        /** Removes one saved product. Returns 204 No Content. */
        @DeleteMapping("/items/{itemId}")
        public ResponseEntity<Void> removeItem(@PathVariable Long itemId) {
                wishlistService.removeItem(itemId);
                return ResponseEntity.noContent().build();
        }
}
