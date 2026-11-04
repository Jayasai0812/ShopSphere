package com.shopsphere;

import com.shopsphere.dto.CartItemRequestDTO;
import com.shopsphere.dto.CartItemUpdateDTO;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.InventoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CartServiceTest {

    @Autowired private CartService cartService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private InventoryRepository inventoryRepository;

    private Long userId;
    private Long productId;
    private Long secondProductId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setName("Cart Customer");
        user.setEmail("cart-customer@shopsphere.test");
        user.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        userRepository.save(user);
        userId = user.getId();

        User seller = new User();
        seller.setName("Cart Seller");
        seller.setEmail("cart-seller@shopsphere.test");
        seller.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        userRepository.save(seller);

        Category category = new Category();
        category.setName("Cart Test Category");
        categoryRepository.save(category);

        Product product = new Product();
        product.setName("Wireless Headphones");
        product.setSku("CART-SKU-001");
        product.setPrice(new BigDecimal("2499.00"));
        product.setCategory(category);
        product.setSeller(seller);
        product.setActive(true);
        Inventory inv = new Inventory();
        inv.setProduct(product);
        inv.setStockQuantity(10);
        product.setInventory(inv);
        productRepository.save(product);
        productId = product.getId();

        Product second = new Product();
        second.setName("Bluetooth Speaker");
        second.setSku("CART-SKU-002");
        second.setPrice(new BigDecimal("1299.00"));
        second.setCategory(category);
        second.setSeller(seller);
        second.setActive(true);
        Inventory inv2 = new Inventory();
        inv2.setProduct(second);
        inv2.setStockQuantity(5);
        second.setInventory(inv2);
        productRepository.save(second);
        secondProductId = second.getId();
    }

    @Test
    void addsNewProductToEmptyCart() {
        var response = cartService.addItem(userId, new CartItemRequestDTO(productId, 2));

        assertThat(response.cartId()).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productName()).isEqualTo("Wireless Headphones");
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("2499.00");
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("4998.00");
        assertThat(response.totalItems()).isEqualTo(2L);
        assertThat(response.totalAmount()).isEqualByComparingTo("4998.00");
    }

    @Test
    void addsSecondProductToExistingCart() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 1));
        var response = cartService.addItem(userId, new CartItemRequestDTO(secondProductId, 2));

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalItems()).isEqualTo(3L);
        assertThat(response.totalAmount()).isEqualByComparingTo("5097.00"); // 2499 + 2*1299
    }

    @Test
    void increasesQuantityWhenProductAlreadyInCart() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 2));
        var response = cartService.addItem(userId, new CartItemRequestDTO(productId, 3));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(5);
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("12495.00");
        assertThat(response.totalItems()).isEqualTo(5L);
    }

    @Test
    void rejectsInactiveProduct() {
        Product product = productRepository.findById(productId).orElseThrow();
        product.setActive(false);
        productRepository.save(product);

        assertThatThrownBy(() ->
                cartService.addItem(userId, new CartItemRequestDTO(productId, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void rejectsWhenCombinedQuantityExceedsStock() {
        // stock is 10, add 8 first then try to add 3 more (combined = 11)
        cartService.addItem(userId, new CartItemRequestDTO(productId, 8));

        assertThatThrownBy(() ->
                cartService.addItem(userId, new CartItemRequestDTO(productId, 3)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void rejectsNonExistentProduct() {
        assertThatThrownBy(() ->
                cartService.addItem(userId, new CartItemRequestDTO(999L, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void rejectsNonExistentUser() {
        assertThatThrownBy(() ->
                cartService.addItem(999L, new CartItemRequestDTO(productId, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void doesNotDeductInventoryWhenAddingToCart() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 3));

        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertThat(inventory.getStockQuantity()).isEqualTo(10); // unchanged
    }

    @Test
    void getCartReturnsEmptyCartWhenNoCartExists() {
        var response = cartService.getCart(userId);

        assertThat(response.cartId()).isNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getCartReturnsAllItemsWithCorrectTotals() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 2));
        cartService.addItem(userId, new CartItemRequestDTO(secondProductId, 1));

        var response = cartService.getCart(userId);

        assertThat(response.cartId()).isNotNull();
        assertThat(response.items()).hasSize(2);
        assertThat(response.totalItems()).isEqualTo(3L);
        assertThat(response.totalAmount()).isEqualByComparingTo("6297.00"); // 2*2499 + 1*1299
    }

    @Test
    void updatesItemQuantitySuccessfully() {
        var cart = cartService.addItem(userId, new CartItemRequestDTO(productId, 2));
        Long itemId = cart.items().get(0).itemId();

        var response = cartService.updateItem(userId, itemId, new CartItemUpdateDTO(4));

        assertThat(response.items().get(0).quantity()).isEqualTo(4);
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("9996.00");
        assertThat(response.totalItems()).isEqualTo(4L);
    }

    @Test
    void updateRejectsQuantityExceedingStock() {
        var cart = cartService.addItem(userId, new CartItemRequestDTO(productId, 2));
        Long itemId = cart.items().get(0).itemId();

        // stock is 10, requesting 11
        assertThatThrownBy(() ->
                cartService.updateItem(userId, itemId, new CartItemUpdateDTO(11)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void updateReturns404ForNonExistentItem() {
        assertThatThrownBy(() ->
                cartService.updateItem(userId, 999L, new CartItemUpdateDTO(2)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cart item not found");
    }

    @Test
    void updateReturns404WhenItemBelongsToAnotherUser() {
        var cart = cartService.addItem(userId, new CartItemRequestDTO(productId, 2));
        Long itemId = cart.items().get(0).itemId();

        User otherUser = new User();
        otherUser.setName("Other");
        otherUser.setEmail("other@shopsphere.test");
        otherUser.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        userRepository.save(otherUser);

        assertThatThrownBy(() ->
                cartService.updateItem(otherUser.getId(), itemId, new CartItemUpdateDTO(1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cart item not found");
    }

    @Test
    void removesItemSuccessfully() {
        var cart = cartService.addItem(userId, new CartItemRequestDTO(productId, 2));
        Long itemId = cart.items().get(0).itemId();

        cartService.removeItem(userId, itemId);

        var updated = cartService.getCart(userId);
        assertThat(updated.items()).isEmpty();
        assertThat(updated.totalItems()).isZero();
    }

    @Test
    void removeReturns404ForNonExistentItem() {
        assertThatThrownBy(() -> cartService.removeItem(userId, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cart item not found");
    }

    @Test
    void removeReturns404WhenItemBelongsToAnotherUser() {
        var cart = cartService.addItem(userId, new CartItemRequestDTO(productId, 1));
        Long itemId = cart.items().get(0).itemId();

        User otherUser = new User();
        otherUser.setName("Other2");
        otherUser.setEmail("other2@shopsphere.test");
        otherUser.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        userRepository.save(otherUser);

        assertThatThrownBy(() -> cartService.removeItem(otherUser.getId(), itemId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cart item not found");
    }

    @Test
    void clearsCartSuccessfully() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 2));
        cartService.addItem(userId, new CartItemRequestDTO(secondProductId, 1));

        cartService.clearCart(userId);

        var response = cartService.getCart(userId);
        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void clearCartOnUserWithNoCartDoesNotThrow() {
        assertThatCode(() -> cartService.clearCart(userId)).doesNotThrowAnyException();
    }
}
