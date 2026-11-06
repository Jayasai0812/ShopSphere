package com.shopsphere.repository;

import com.shopsphere.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    /**
     * Loads the wishlist with its items in one query. Items are LAZY on
     * the entity; without the join each items access triggers an extra
     * query while building the response DTO.
     */
    @Query("SELECT w FROM Wishlist w LEFT JOIN FETCH w.items WHERE w.user.id = :userId")
    Optional<Wishlist> findByUserIdWithItems(@Param("userId") Long userId);

    /** Existence check used before creating the implicit wishlist. */
    boolean existsByUserId(Long userId);
}
