package com.shopsphere.repository;

import com.shopsphere.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Order history for a customer, newest first.
     * Use with {@code findByUserId(userId, Pageable)} and pass
     * {@code PageRequest.of(page, size, Sort.by(DESC, "createdAt"))}.
     */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /**
     * Ownership-scoped lookup for the details endpoint. Returns empty when the
     * order exists but belongs to another customer, so the service can answer
     * 404 without revealing the order's existence.
     */
    Optional<Order> findByIdAndUserId(Long id, Long userId);
}
