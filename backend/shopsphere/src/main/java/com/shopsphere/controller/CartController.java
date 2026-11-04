package com.shopsphere.controller;

import com.shopsphere.common.CurrentUserProvider;
import com.shopsphere.dto.CartItemRequestDTO;
import com.shopsphere.dto.CartResponseDTO;
import com.shopsphere.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;
    private final CurrentUserProvider currentUserProvider;

    public CartController(CartService cartService, CurrentUserProvider currentUserProvider) {
        this.cartService = cartService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponseDTO> addItem(@Valid @RequestBody CartItemRequestDTO request) {
        return ResponseEntity.ok(cartService.addItem(currentUserProvider.getCurrentUserId(), request));
    }

    @GetMapping
    public ResponseEntity<CartResponseDTO> getCart() {
        return ResponseEntity.ok(cartService.getCart(currentUserProvider.getCurrentUserId()));
    }
}
