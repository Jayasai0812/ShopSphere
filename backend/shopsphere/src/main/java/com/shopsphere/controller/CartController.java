package com.shopsphere.controller;

import com.shopsphere.common.CurrentUserProvider;
import com.shopsphere.dto.CartItemRequestDTO;
import com.shopsphere.dto.CartItemUpdateDTO;
import com.shopsphere.dto.CartResponseDTO;
import com.shopsphere.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartResponseDTO> updateItem(
            @PathVariable Long itemId,
            @Valid @RequestBody CartItemUpdateDTO request) {
        return ResponseEntity.ok(
                cartService.updateItem(currentUserProvider.getCurrentUserId(), itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long itemId) {
        cartService.removeItem(currentUserProvider.getCurrentUserId(), itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        cartService.clearCart(currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
