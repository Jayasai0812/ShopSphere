package com.shopsphere.controller;

import com.shopsphere.common.CurrentUserProvider;
import com.shopsphere.dto.CheckoutRequestDTO;
import com.shopsphere.dto.OrderDetailsResponseDTO;
import com.shopsphere.dto.OrderHistoryResponseDTO;
import com.shopsphere.dto.OrderResponseDTO;
import com.shopsphere.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserProvider currentUserProvider;

    public OrderController(OrderService orderService,
                           CurrentUserProvider currentUserProvider) {
        this.orderService = orderService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * POST /api/v1/orders
     * <p>
     * Creates an order from the authenticated user's cart. The request only
     * carries the shipping {@code addressId}; prices, quantities and totals
     * are resolved server-side from the database.
     */
    @PostMapping
    public ResponseEntity<OrderResponseDTO> checkout(
            @Valid @RequestBody CheckoutRequestDTO request) {

        OrderResponseDTO response = orderService.checkout(
                currentUserProvider.getCurrentUserId(), request);

        URI location = URI.create("/api/v1/orders/" + response.orderId());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * GET /api/v1/orders
     * <p>
     * Paginated order history for the authenticated customer, newest first.
     * The user ID comes only from {@link CurrentUserProvider} — never from a
     * query parameter or request body — so a customer can only ever see their
     * own orders.
     */
    @GetMapping
    public ResponseEntity<Page<OrderHistoryResponseDTO>> getMyOrders(
            @PageableDefault(
                size = 10,
                sort = "createdAt",
                direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return ResponseEntity.ok(
                orderService.getMyOrders(currentUserProvider.getCurrentUserId(), pageable));
    }

    /**
     * GET /api/v1/orders/{orderId}
     * <p>
     * Full details of one order, only if it belongs to the authenticated
     * customer. Ownership is enforced in the service layer via the
     * ownership-scoped repository lookup, so both "someone else's order" and
     * "no such order" surface as 404.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailsResponseDTO> getOrderDetails(
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(
                orderService.getOrderDetails(currentUserProvider.getCurrentUserId(), orderId));
    }
}
