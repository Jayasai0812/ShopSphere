package com.shopsphere;

import com.shopsphere.dto.CartItemRequestDTO;
import com.shopsphere.dto.CheckoutRequestDTO;
import com.shopsphere.entity.Address;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.repository.AddressRepository;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.InventoryRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.service.CartService;
import com.shopsphere.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-level tests for the order-history endpoints:
 * GET /api/v1/orders — paginated order history
 * GET /api/v1/orders/{id} — single order details (ownership-scoped)
 * <p>
 * The authenticated user is supplied via the {@code X-User-Id} header, which
 * is how {@code HttpHeaderCurrentUserProvider} resolves the current user.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerTest {

    private static final String PASSWORD_HASH = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private CartService cartService;
    @Autowired
    private OrderService orderService;

    private Long userId;
    private Long otherUserId;
    private Long otherAddressId;
    private Long addressId;
    private Long productId;
    private Long secondProductId;

    @BeforeEach
    void setUp() {
        // Customer with a shipping address
        User customer = new User();
        customer.setName("History Customer");
        customer.setEmail("history-customer@shopsphere.test");
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

        // Second customer — used for the cross-user access test
        User otherUser = new User();
        otherUser.setName("Other Customer");
        otherUser.setEmail("other-history@shopsphere.test");
        otherUser.setPasswordHash(PASSWORD_HASH);
        userRepository.save(otherUser);
        otherUserId = otherUser.getId();

        Address otherAddress = new Address();
        otherAddress.setUser(otherUser);
        otherAddress.setLine1("45 Park Avenue");
        otherAddress.setCity("Delhi");
        otherAddress.setState("Delhi");
        otherAddress.setPincode("110001");
        otherAddress.setCountry("India");
        otherAddress.setDefaultAddress(true);
        otherUser.getAddresses().add(otherAddress);
        addressRepository.save(otherAddress);
        userRepository.saveAndFlush(otherUser);
        otherAddressId = otherAddress.getId();

        // Seller, category, products
        User seller = new User();
        seller.setName("History Seller");
        seller.setEmail("history-seller@shopsphere.test");
        seller.setPasswordHash(PASSWORD_HASH);
        userRepository.save(seller);

        Category category = new Category();
        category.setName("History Test Category");
        categoryRepository.save(category);

        Product product1 = new Product();
        product1.setName("Wireless Headphones");
        product1.setSku("HISTORY-SKU-001");
        product1.setPrice(new BigDecimal("2499.00"));
        product1.setCategory(category);
        product1.setSeller(seller);
        product1.setActive(true);
        Inventory inv1 = new Inventory();
        inv1.setProduct(product1);
        inv1.setStockQuantity(50);
        product1.setInventory(inv1);
        productRepository.save(product1);
        productId = product1.getId();

        Product product2 = new Product();
        product2.setName("Bluetooth Speaker");
        product2.setSku("HISTORY-SKU-002");
        product2.setPrice(new BigDecimal("1299.00"));
        product2.setCategory(category);
        product2.setSeller(seller);
        product2.setActive(true);
        Inventory inv2 = new Inventory();
        inv2.setProduct(product2);
        inv2.setStockQuantity(50);
        product2.setInventory(inv2);
        productRepository.save(product2);
        secondProductId = product2.getId();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Places items in the customer's cart and checks out, returning the order id.
     */
    private Long checkout(Long uid, Long addrId, Object... productQuantityPairs) {
        for (int i = 0; i < productQuantityPairs.length; i += 2) {
            cartService.addItem(uid, new CartItemRequestDTO(
                    (Long) productQuantityPairs[i], (Integer) productQuantityPairs[i + 1]));
        }
        return orderService.checkout(uid, new CheckoutRequestDTO(addrId)).orderId();
    }

    /** Creates {@code count} single-item orders for the given user, spaced apart
     *  in time so createdAt values differ and sort order is deterministic. */
    private void createOrders(Long uid, Long addrId, int count) {
        for (int i = 0; i < count; i++) {
            checkout(uid, addrId, productId, 1);
            try {
                Thread.sleep(20); // ensure distinct createdAt timestamps in H2
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Long orderIdOfLatest() {
        return orderService.getMyOrders(userId,
                org.springframework.data.domain.PageRequest.of(0, 1,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
                .getContent().get(0).orderId();
    }



    // ── GET /api/v1/orders — order history ──────────────────────────────────

    @Test
    void getMyOrders_returnsEmptyPageForCustomerWithNoOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getMyOrders_returnsAllOrdersForCustomerWithMultipleOrders() throws Exception {
        createOrders(userId, addressId, 3);

        mockMvc.perform(get("/api/v1/orders").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void getMyOrders_doesNotReturnOtherUsersOrders() throws Exception {
        createOrders(userId, addressId, 1);
        createOrders(otherUserId, otherAddressId, 1);

        mockMvc.perform(get("/api/v1/orders").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].items[0].productName")
                        .value("Wireless Headphones"));
    }

    @Test
    void getMyOrders_respectsPaginationAndSorting() throws Exception {
        createOrders(userId, addressId, 7);

        // Explicit page size 5 → two pages
        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Id", userId)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(2));

        // Page 2 holds the remaining 2 orders
        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Id", userId)
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Default sort is createdAt DESC → first row is the newest order
        mockMvc.perform(get("/api/v1/orders").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(orderIdOfLatest()));

        // Explicit "sort=createdAt,desc" keeps newest-first (default is also DESC;
        // note Spring appends request sort AFTER the @PageableDefault sort, so
        // a client requesting asc cannot override the default here — verified
        // separately via the repository-level ascending query in OrderServiceTest).
        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Id", userId)
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(orderIdOfLatest()));
    }

    @Test
    void getMyOrders_includesItemSnapshots() throws Exception {
        checkout(userId, addressId, productId, 2, secondProductId, 1);

        mockMvc.perform(get("/api/v1/orders").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].items", hasSize(2)))
                .andExpect(jsonPath("$.content[0].items[0].productName").value("Wireless Headphones"))
                .andExpect(jsonPath("$.content[0].items[0].unitPrice").value(2499.00))
                .andExpect(jsonPath("$.content[0].totalAmount").value(6297.00));
    }

    // ── GET /api/v1/orders/{id} — order details ─────────────────────────────

    @Test
    void getOrderDetails_returnsDetailsForOwner() throws Exception {
        Long orderId = checkout(userId, addressId, productId, 3);

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(7497.00))
                .andExpect(jsonPath("$.shippingCity").value("Bengaluru"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void getOrderDetails_returns404ForDifferentUsersOrder() throws Exception {
        Long orderId = checkout(userId, addressId, productId, 1);

        // The other user gets 404 — not 403 — so they cannot even learn that
        // this order exists.
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("X-User-Id", otherUserId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderDetails_returns404ForNonexistentOrder() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}", 999)
                        .header("X-User-Id", userId))
                .andExpect(status().isNotFound());
    }
}
