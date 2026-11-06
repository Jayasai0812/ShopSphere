package com.shopsphere.service;

import com.shopsphere.common.CurrentUserProvider;
import com.shopsphere.dto.WishlistItemResponseDTO;
import com.shopsphere.dto.WishlistResponseDTO;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.entity.Wishlist;
import com.shopsphere.entity.WishlistItem;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.InventoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.repository.WishlistItemRepository;
import com.shopsphere.repository.WishlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Wishlist management for the logged-in user (TASK-017).
 *
 * Identity and ownership rules:
 *  - The owning user is ALWAYS resolved from the JWT security context via
 *    {@link CurrentUserProvider}; no user ID is ever accepted from a request.
 *  - The wishlist itself is implicit: created on the first add. A user
 *    never sees another user's wishlist — there is no wishlist-id path
 *    variable anywhere; items are addressed by their own id, and the
 *    service additionally verifies the item's wishlist belongs to the
 *    caller (404 otherwise, so existence is not leaked).
 *
 * Duplicate rule:
 *  - One product can appear at most once per user's wishlist. The common
 *    repeated-add case is caught by an exists-check (409 CONFLICT); the
 *    (wishlist_id, product_id) unique constraint on wishlist_items remains
 *    as the hard backstop against concurrent duplicate adds, whose
 *    DataIntegrityViolation is also mapped to 409.
 *
 * Product rules:
 *  - Only active products can be saved; inactive ones return 409.
 *  - A deleted product's wishlist rows are removed with it.
 */
@Service
public class WishlistService {

        private final WishlistRepository wishlistRepository;
        private final WishlistItemRepository wishlistItemRepository;
        private final UserRepository userRepository;
        private final ProductRepository productRepository;
        private final InventoryRepository inventoryRepository;
        private final CurrentUserProvider currentUserProvider;

        public WishlistService(WishlistRepository wishlistRepository,
                        WishlistItemRepository wishlistItemRepository,
                        UserRepository userRepository,
                        ProductRepository productRepository,
                        InventoryRepository inventoryRepository,
                        CurrentUserProvider currentUserProvider) {
                this.wishlistRepository = wishlistRepository;
                this.wishlistItemRepository = wishlistItemRepository;
                this.userRepository = userRepository;
                this.productRepository = productRepository;
                this.inventoryRepository = inventoryRepository;
                this.currentUserProvider = currentUserProvider;
        }

        /** Returns the caller's wishlist; empty list if nothing saved yet. */
        @Transactional(readOnly = true)
        public WishlistResponseDTO getWishlist() {
                Long userId = currentUserId();
                return wishlistRepository.findByUserIdWithItems(userId)
                                .<WishlistResponseDTO>map(this::toDto)
                                .orElse(new WishlistResponseDTO(null, List.of()));
        }

        /**
         * Adds a product to the caller's wishlist. Creates the implicit
         * wishlist on first add (concurrent creation is handled the same
         * way CartService handles it). Duplicates return 409.
         */
        @Transactional
        public WishlistItemResponseDTO addItem(com.shopsphere.dto.WishlistAddRequestDTO request) {
                Long userId = currentUserId();

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                Wishlist wishlist = wishlistRepository.findByUserIdWithItems(userId)
                                .orElseGet(() -> createWishlistFor(user));

                Product product = productRepository.findById(request.productId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Product not found with id: " + request.productId()));

                if (!product.isActive()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Product is not available: " + product.getName());
                }

                if (wishlistItemRepository.existsByWishlistIdAndProductId(wishlist.getId(), product.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Product is already in the wishlist");
                }

                WishlistItem item = new WishlistItem();
                item.setWishlist(wishlist);
                item.setProduct(product);
                try {
                        item = wishlistItemRepository.saveAndFlush(item);
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        // Concurrent duplicate add hit the unique constraint.
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Product is already in the wishlist");
                }
                return toDto(item);
        }

        /** Removes one owned wishlist item; 404 if missing or not the caller's. */
        @Transactional
        public void removeItem(Long itemId) {
                WishlistItem item = wishlistItemRepository
                                .findByIdAndWishlistUserId(itemId, currentUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found"));
                wishlistItemRepository.delete(item);
        }

        /**
         * Removes every wishlist row referencing the given product. Called
         * from the product deletion flow to prevent dangling references.
         */
        @Transactional
        public void removeItemsForProduct(Long productId) {
                wishlistItemRepository.deleteByProductId(productId);
        }

        /**
         * Creates the caller's implicit wishlist. Concurrent first-adds are
         * resolved by the users-wishlist unique constraint, mirroring the
         * CartService pattern.
         */
        private Wishlist createWishlistFor(User user) {
                try {
                        Wishlist wishlist = new Wishlist();
                        wishlist.setUser(user);
                        return wishlistRepository.saveAndFlush(wishlist);
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        return wishlistRepository.findByUserIdWithItems(user.getId())
                                        .orElseThrow(() -> new ResponseStatusException(
                                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                                        "Wishlist creation failed"));
                }
        }

        private WishlistItemResponseDTO toDto(WishlistItem item) {
                Product product = item.getProduct();
                boolean available = product.isActive() && stockOf(product) > 0;
                return new WishlistItemResponseDTO(
                                item.getId(),
                                product.getId(),
                                product.getName(),
                                product.getSku(),
                                product.getPrice(),
                                available);
        }

        private int stockOf(Product product) {
                return inventoryRepository.findByProductId(product.getId())
                                .map(Inventory::getStockQuantity)
                                .orElse(0);
        }

        private WishlistResponseDTO toDto(Wishlist wishlist) {
                List<WishlistItemResponseDTO> items = wishlistItemRepository
                                .findByWishlistIdOrderByIdAsc(wishlist.getId())
                                .stream()
                                .map(this::toDto)
                                .toList();
                return new WishlistResponseDTO(wishlist.getId(), items);
        }

        private Long currentUserId() {
                return currentUserProvider.getCurrentUserId();
        }
}
