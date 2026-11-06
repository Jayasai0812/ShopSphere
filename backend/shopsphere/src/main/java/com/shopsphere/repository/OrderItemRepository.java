package com.shopsphere.repository;

import com.shopsphere.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * True when the user has at least one DELIVERED order containing the
     * product — the purchase proof for reviewing (TASK-018). Verification
     * is based on actual order history, never on client-supplied claims.
     */
    @Query("""
            SELECT COUNT(oi.id) > 0
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.user.id = :userId
              AND oi.product.id = :productId
              AND o.status = com.shopsphere.enums.OrderStatus.DELIVERED
            """)
    boolean existsDeliveredPurchase(@Param("userId") Long userId,
                                    @Param("productId") Long productId);
}
