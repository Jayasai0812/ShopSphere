package com.shopsphere.repository;

import com.shopsphere.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    /**
     * Deterministic listing order: lowest item id first, which equals the
     * order in which products were saved (IDENTITY ids). The frontend gets
     * a stable list between calls instead of arbitrary database order.
     */
    List<WishlistItem> findByWishlistIdOrderByIdAsc(Long wishlistId);

    /**
     * Fast duplicate probe. The (wishlist_id, product_id) unique constraint
     * on wishlist_items is the hard guarantee; this exists-check avoids
     * relying on constraint violations for the common repeated-add case.
     */
    boolean existsByWishlistIdAndProductId(Long wishlistId, Long productId);

    /** Owned-item lookup: the only path the service uses to touch an item. */
    Optional<WishlistItem> findByIdAndWishlistUserId(Long id, Long userId);

    /**
     * Removes every wishlist row pointing at a deleted product. Called by
     * product deletion flow so no dangling rows remain.
     */
    void deleteByProductId(Long productId);
}
