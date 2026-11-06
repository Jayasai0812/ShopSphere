package com.shopsphere.repository;

import com.shopsphere.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** Images of one product ordered by the dense sort order. */
    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    /** The current primary image of a product, if any. */
    Optional<ProductImage> findByProductIdAndPrimaryImageTrue(Long productId);

    @Modifying
    @Query("DELETE FROM ProductImage i WHERE i.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    /** Batched lookup for listing pages: images of many products, ordered. */
    List<ProductImage> findByProductIdInOrderByProductIdAscSortOrderAsc(
                    List<Long> productIds);
}
