package com.shopsphere;

import com.shopsphere.dto.CartItemRequestDTO;
import com.shopsphere.dto.CheckoutRequestDTO;
import com.shopsphere.entity.Address;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.InventoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.service.CartService;
import com.shopsphere.service.OrderService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that a failure HALFWAY through checkout rolls back earlier changes.
 * <p>
 * Deliberately NOT inside a test-managed transaction: the test class has no
 * {@code @Transactional} at class level, so the checkout call runs in its own
 * real transaction that is genuinely rolled back when it fails. Inside a
 * joined test transaction the changes were never committed, so a rollback
 * could not be observed (stock would still appear decremented afterwards).
 * <p>
 * The test creates its own committed data with unique emails/SKUs and cleans
 * up in a finally block, so it leaves no residue for the transactional tests
 * that share the same in-memory database.
 */
@SpringBootTest
class CheckoutRollbackTest {

    private static final String PASSWORD_HASH =
            "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void midCheckoutFailureRollsBackEarlierInventoryAndCart() {
        User customer = new User();
        customer.setName("Rollback Customer");
        customer.setEmail("rollback-customer@shopsphere.test");
        customer.setPasswordHash(PASSWORD_HASH);

        Address address = new Address();
        address.setUser(customer);
        address.setLine1("2 Test Street");
        address.setCity("Mumbai");
        address.setState("Maharashtra");
        address.setPincode("400001");
        address.setCountry("India");
        address.setDefaultAddress(true);
        customer.getAddresses().add(address);
        userRepository.save(customer);

        User seller = new User();
        seller.setName("Rollback Seller");
        seller.setEmail("rollback-seller@shopsphere.test");
        seller.setPasswordHash(PASSWORD_HASH);
        userRepository.save(seller);

        Category category = new Category();
        category.setName("Rollback Test Category");
        categoryRepository.save(category);

        // Product A — sufficient stock for the requested quantity.
        Product productA = new Product();
        productA.setName("Rollback Product A");
        productA.setSku("ROLLBACK-SKU-A");
        productA.setPrice(new BigDecimal("1000.00"));
        productA.setCategory(category);
        productA.setSeller(seller);
        productA.setActive(true);
        Inventory invA = new Inventory();
        invA.setProduct(productA);
        invA.setStockQuantity(10);
        productA.setInventory(invA);
        productRepository.save(productA);

        // Product B — stock is cut below the requested quantity before checkout,
        // so checkout fails AFTER product A's inventory has been decremented.
        Product productB = new Product();
        productB.setName("Rollback Product B");
        productB.setSku("ROLLBACK-SKU-B");
        productB.setPrice(new BigDecimal("500.00"));
        productB.setCategory(category);
        productB.setSeller(seller);
        productB.setActive(true);
        Inventory invB = new Inventory();
        invB.setProduct(productB);
        invB.setStockQuantity(5);
        productB.setInventory(invB);
        productRepository.save(productB);

        try {
            // Cart: 2 of product A (stock 10 — sufficient) and 5 of product B.
            cartService.addItem(customer.getId(), new CartItemRequestDTO(productA.getId(), 2));
            cartService.addItem(customer.getId(), new CartItemRequestDTO(productB.getId(), 5));

            // Cut product B's stock below the requested quantity.
            Inventory invBNow = inventoryRepository.findByProductId(productB.getId()).orElseThrow();
            invBNow.setStockQuantity(3);
            inventoryRepository.save(invBNow);

            // Checkout must fail on the second product...
            assertThatThrownBy(() ->
                    orderService.checkout(customer.getId(),
                            new CheckoutRequestDTO(address.getId())))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Insufficient stock");

            // ...and its transaction must have rolled back completely:
            // product A's stock was decremented inside the failed transaction,
            // so it must still be 10 in the database.
            entityManager.clear(); // detach stale entities — force re-read from DB
            Inventory invAAfter = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
            assertThat(invAAfter.getStockQuantity()).isEqualTo(10);

            Inventory invBAfter = inventoryRepository.findByProductId(productB.getId()).orElseThrow();
            assertThat(invBAfter.getStockQuantity()).isEqualTo(3); // only our own committed change

            // Both cart items must remain.
            var cart = cartService.getCart(customer.getId());
            assertThat(cart.items()).hasSize(2);
            assertThat(cart.items()).extracting("productId")
                    .containsExactlyInAnyOrder(productA.getId(), productB.getId());
        } finally {
            // Clean up committed data in a fresh persistence context — the
            // failed checkout left stale versioned entities in this one.
            entityManager.clear();
            cartService.clearCart(customer.getId());
            productRepository.findById(productA.getId()).ifPresent(productRepository::delete);
            productRepository.findById(productB.getId()).ifPresent(productRepository::delete);
            userRepository.findById(customer.getId()).ifPresent(userRepository::delete);
            userRepository.findById(seller.getId()).ifPresent(userRepository::delete);
            categoryRepository.findById(category.getId()).ifPresent(categoryRepository::delete);
        }
    }
}
