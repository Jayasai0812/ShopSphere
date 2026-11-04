package com.shopsphere.service;

import com.shopsphere.dto.CartItemRequestDTO;
import com.shopsphere.dto.CartItemResponseDTO;
import com.shopsphere.dto.CartResponseDTO;
import com.shopsphere.entity.Cart;
import com.shopsphere.entity.CartItem;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.CartRepository;
import com.shopsphere.repository.InventoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       InventoryRepository inventoryRepository,
                       UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
    }

    public CartResponseDTO addItem(Long userId, CartItemRequestDTO request) {
        // 1. Verify user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));

        // 2. Find or create cart
        Cart cart = cartRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(user);
                    return cartRepository.save(newCart);
                });

        // 3. Verify product exists
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + request.productId()));

        // 4. Verify product is active
        if (!product.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Product is not available: " + product.getName());
        }

        // 5. Retrieve inventory
        Inventory inventory = inventoryRepository.findByProductId(product.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for product id: " + product.getId()));

        // 6 & 7 & 8. Check for existing cart item and validate combined stock
        CartItem cartItem = cartItemRepository
                .findByCart_IdAndProduct_Id(cart.getId(), product.getId())
                .orElse(null);

        // Improvement A: use long to prevent integer overflow
        long newQuantity = (cartItem != null)
                ? (long) cartItem.getQuantity() + request.quantity()
                : request.quantity();

        // 6. Validate combined quantity against stock (inventory not deducted)
        if (newQuantity > inventory.getStockQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Insufficient stock. Available: " + inventory.getStockQuantity()
                            + ", Requested: " + newQuantity);
        }

        // 9. Save cart item
        if (cartItem != null) {
            cartItem.setQuantity(Math.toIntExact(newQuantity));
        } else {
            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setQuantity(Math.toIntExact(newQuantity));
            cart.getItems().add(cartItem);
        }
        cartItemRepository.save(cartItem);

        // 10. Return full cart response
        return toCartResponse(cart);
    }

    @Transactional(readOnly = true)
    public CartResponseDTO getCart(Long userId) {
        return cartRepository.findByUser_Id(userId)
                .map(this::toCartResponse)
                .orElse(new CartResponseDTO(null, List.of(), 0, BigDecimal.ZERO));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CartResponseDTO toCartResponse(Cart cart) {
        List<CartItemResponseDTO> itemDTOs = cartItemRepository
                .findByCartId(cart.getId())
                .stream()
                .map(this::toItemResponse)
                .toList();

        BigDecimal total = itemDTOs.stream()
                .map(CartItemResponseDTO::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalItems = itemDTOs.stream()
                .mapToInt(CartItemResponseDTO::quantity)
                .sum();

        return new CartResponseDTO(cart.getId(), itemDTOs, totalItems, total);
    }

    private CartItemResponseDTO toItemResponse(CartItem item) {
        BigDecimal unitPrice = item.getProduct().getPrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponseDTO(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                unitPrice,
                subtotal
        );
    }
}
