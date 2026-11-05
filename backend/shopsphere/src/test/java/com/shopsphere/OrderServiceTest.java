package com.shopsphere;

import com.shopsphere.dto.CartItemRequestDTO;
import com.shopsphere.dto.CheckoutRequestDTO;
import com.shopsphere.dto.OrderResponseDTO;
import com.shopsphere.entity.Address;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.InventoryRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.service.CartService;
import com.shopsphere.service.OrderService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OrderServiceTest {

    private static final String PASSWORD_HASH =
            "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;

    private Long userId;
    private Long addressId;
    private Long otherAddressId;
    private Long productId;
    private Long secondProductId;

    @BeforeEach
    void setUp() {
        // Customer with a shipping address
        User customer = new User();
        customer.setName("Checkout Customer");
        customer.setEmail("checkout-customer@shopsphere.test");
        customer.setPasswordHash(PASSWORD_HASH);

        Address address = new Address();
        address.setUser(customer);
        address.setLine1("14 Residency Road");
        address.setCity("Bengaluru");
        address.setState("Karnataka");
        address.setPincode("560001");
        address.setCountry("India");
        address.setDefaultAddress(true);
        customer.getAddresses().add(address);

        userRepository.save(customer);
        userId = customer.getId();
        addressId = address.getId();

        // Another user with their own address (for ownership tests)
        User otherUser = new User();
        otherUser.setName("Other User");
        otherUser.setEmail("other-checkout@shopsphere.test");
        otherUser.setPasswordHash(PASSWORD_HASH);

        Address otherAddress = new Address();
        otherAddress.setUser(otherUser);
        otherAddress.setLine1("45 Park Avenue");
        otherAddress.setCity("Delhi");
        otherAddress.setState("Delhi");
        otherAddress.setPincode("110001");
        otherAddress.setCountry("India");
        otherAddress.setDefaultAddress(true);
        otherUser.getAddresses().add(otherAddress);

        userRepository.save(otherUser);
        otherAddressId = otherAddress.getId();

        // Seller
        User seller = new User();
        seller.setName("Checkout Seller");
        seller.setEmail("checkout-seller@shopsphere.test");
        seller.setPasswordHash(PASSWORD_HASH);
        userRepository.save(seller);

        // Category
        Category category = new Category();
        category.setName("Checkout Test Category");
        categoryRepository.save(category);

        // Product 1 — Wireless Headphones, price 2499, stock 10
        Product product1 = new Product();
        product1.setName("Wireless Headphones");
        product1.setSku("CHECKOUT-SKU-001");
        product1.setPrice(new BigDecimal("2499.00"));
        product1.setCategory(category);
        product1.setSeller(seller);
        product1.setActive(true);
        Inventory inv1 = new Inventory();
        inv1.setProduct(product1);
        inv1.setStockQuantity(10);
        product1.setInventory(inv1);
        productRepository.save(product1);
        productId = product1.getId();

        // Product 2 — Bluetooth Speaker, price 1299, stock 5
        Product product2 = new Product();
        product2.setName("Bluetooth Speaker");
        product2.setSku("CHECKOUT-SKU-002");
        product2.setPrice(new BigDecimal("1299.00"));
        product2.setCategory(category);
        product2.setSeller(seller);
        product2.setActive(true);
        Inventory inv2 = new Inventory();
        inv2.setProduct(product2);
        inv2.setStockQuantity(5);
        product2.setInventory(inv2);
        productRepository.save(product2);
        secondProductId = product2.getId();
    }

    // ── successful checkout ──────────────────────────────────────────────────

    @Test
    void successfulCheckout() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 3));

        OrderResponseDTO response =
                orderService.checkout(userId, new CheckoutRequestDTO(addressId));

        assertThat(response.orderId()).isNotNull();
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalAmount()).isEqualByComparingTo("7497.00"); // 3 * 2499
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(productId);
        assertThat(response.items().get(0).productName()).isEqualTo("Wireless Headphones");
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("2499.00");
        assertThat(response.items().get(0).lineTotal()).isEqualByComparingTo("7497.00");
        // shipping address snapshot
        assertThat(response.shippingAddressLine1()).isEqualTo("14 Residency Road");
        assertThat(response.shippingCity()).isEqualTo("Bengaluru");
        assertThat(response.shippingState()).isEqualTo("Karnataka");
        assertThat(response.shippingPincode()).isEqualTo("560001");
        assertThat(response.shippingCountry()).isEqualTo("India");
    }

    @Test
    void checkoutDoesNotCreatePayment() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 1));

        OrderResponseDTO response =
                orderService.checkout(userId, new CheckoutRequestDTO(addressId));

        Order order = orderRepository.findById(response.orderId()).orElseThrow();
        assertThat(order.getPayments()).isEmpty();
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    void emptyCartRejected() {
        assertThatThrownBy(() ->
                orderService.checkout(userId, new CheckoutRequestDTO(addressId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cart is empty");
    }

    @Test
    void invalidAddressIdRejected() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 1));

        assertThatThrownBy(() ->
                orderService.checkout(userId, new CheckoutRequestDTO(999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Address not found");
    }

    @Test
    void addressBelongingToAnotherUserRejected() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 1));

        assertThatThrownBy(() ->
                orderService.checkout(userId, new CheckoutRequestDTO(otherAddressId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Address not found");
    }

    @Test
    void inactiveProductRejected() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 1));

        Product product = productRepository.findById(productId).orElseThrow();
        product.setActive(false);
        productRepository.save(product);

        assertThatThrownBy(() ->
                orderService.checkout(userId, new CheckoutRequestDTO(addressId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void insufficientInventoryRejected() {
        // Add 10 to cart (stock is 10, passes cart-service check)
        cartService.addItem(userId, new CartItemRequestDTO(productId, 10));

        // Then drop stock to 5 (simulating a sale or restock reduction after cart was filled)
        Inventory inv = inventoryRepository.findByProductId(productId).orElseThrow();
        entityManager.createNativeQuery(
                "UPDATE inventory SET stock_quantity = 5 WHERE id = :id")
                .setParameter("id", inv.getId())
                .executeUpdate();
        entityManager.clear(); // force service to re-read from DB

        assertThatThrownBy(() ->
                orderService.checkout(userId, new CheckoutRequestDTO(addressId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock");
    }

    // ── multi-item checkout ───────────────────────────────────────────────────

    @Test
    void multipleProductsInCart() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 3));
        cartService.addItem(userId, new CartItemRequestDTO(secondProductId, 2));

        OrderResponseDTO response =
                orderService.checkout(userId, new CheckoutRequestDTO(addressId));

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalAmount()).isEqualByComparingTo("10095.00"); // 3*2499 + 2*1299
    }

    // ── side-effect verification ───────────────────────────────────────────────

    @Test
    void inventoryDecreasesCorrectly() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 3));

        orderService.checkout(userId, new CheckoutRequestDTO(addressId));

        Inventory inv = inventoryRepository.findByProductId(productId).orElseThrow();
        assertThat(inv.getStockQuantity()).isEqualTo(7); // 10 - 3
    }

    @Test
    void cartBecomesEmptyAfterCheckout() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 3));
        cartService.addItem(userId, new CartItemRequestDTO(secondProductId, 1));

        orderService.checkout(userId, new CheckoutRequestDTO(addressId));

        var cart = cartService.getCart(userId);
        assertThat(cart.items()).isEmpty();
        assertThat(cart.totalItems()).isZero();
    }

    @Test
    void failedCheckoutDoesNotChangeInventoryOrCart() {
        cartService.addItem(userId, new CartItemRequestDTO(productId, 3));

        // Deactivate product so checkout fails at the active-check step,
        // before any inventory change or cart clear
        Product product = productRepository.findById(productId).orElseThrow();
        product.setActive(false);
        productRepository.save(product);

        assertThatThrownBy(() ->
                orderService.checkout(userId, new CheckoutRequestDTO(addressId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not available");

        // Inventory must be untouched
        Inventory inv = inventoryRepository.findByProductId(productId).orElseThrow();
        assertThat(inv.getStockQuantity()).isEqualTo(10);

        // Cart must still contain the item
        var cart = cartService.getCart(userId);
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(3);
    }

    // ── optimistic locking ────────────────────────────────────────────────

    /**
     * Stale-version conflict test — NOT a true simultaneous-checkout test.
     * <p>
     * It simulates a competing checkout that has already committed by updating
     * the inventory row directly in SQL (stock and version), leaving the
     * persistence-context entity stale. The service then reads that stale
     * entity, passes the stock check, and fails at flush time when the
     * {@code @Version} in the UPDATE's WHERE clause no longer matches the DB.
     * <p>
     * This proves the optimistic-lock mechanism rejects writes based on a stale
     * read, but it does not exercise two independent transactions racing at the
     * same instant. A true concurrency test (two threads/transactions calling
     * checkout concurrently) should be added later as an integration test.
     */
    @Test
    void concurrentCheckoutsCannotOversell() {
        // Stock = 5, cart requests 5 — a valid single checkout
        cartService.addItem(userId, new CartItemRequestDTO(productId, 5));

        // Simulate a concurrent checkout that already committed: directly
        // decrement stock and bump @Version in the DB, bypassing the persistence
        // context so the cached entity still holds the stale version=0.
        Inventory inv = inventoryRepository.findByProductId(productId).orElseThrow();
        entityManager.createNativeQuery(
                "UPDATE inventory SET stock_quantity = stock_quantity - 5, " +
                "version = version + 1 WHERE id = :id")
                .setParameter("id", inv.getId())
                .executeUpdate();
        // Deliberately do NOT clear() — the service must read the stale cached
        // entity (stock=5, version=0), pass the stock check, then fail at flush
        // when the @Version in the WHERE clause no longer matches the DB.

        assertThatThrownBy(() ->
                orderService.checkout(userId, new CheckoutRequestDTO(addressId)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
