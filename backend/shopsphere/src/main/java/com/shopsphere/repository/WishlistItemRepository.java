package com.shopsphere.repository;

import com.shopsphere.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByWishlistId(Long wishlistId);
}
