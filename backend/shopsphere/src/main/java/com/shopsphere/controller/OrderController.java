package com.shopsphere.controller;

import com.shopsphere.common.CurrentUserProvider;
import com.shopsphere.dto.CheckoutRequestDTO;
import com.shopsphere.dto.OrderResponseDTO;
import com.shopsphere.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
}
