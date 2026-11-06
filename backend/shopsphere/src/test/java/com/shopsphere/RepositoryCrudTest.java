package com.shopsphere;

import com.shopsphere.entity.Address;
import com.shopsphere.entity.Cart;
import com.shopsphere.entity.CartItem;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.OrderItem;
import com.shopsphere.entity.Payment;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.Role;
import com.shopsphere.entity.User;
import com.shopsphere.entity.Wishlist;
import com.shopsphere.entity.WishlistItem;
import com.shopsphere.enums.OrderStatus;
import com.shopsphere.enums.PaymentStatus;
import com.shopsphere.repository.AddressRepository;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.CartRepository;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.InventoryRepository;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.PaymentRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.RoleRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.repository.WishlistItemRepository;
import com.shopsphere.repository.WishlistRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RepositoryCrudTest {

    private static final String PASSWORD_HASH =
            "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsReadsUpdatesAndDeletesUser() {
        User user = user("Meera Iyer", "meera@shopsphere.test");
        User saved = userRepository.saveAndFlush(user);

        User found = userRepository.findByEmail("meera@shopsphere.test").orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getPasswordHash()).startsWith("$2a$");
        assertThat(found.getCreatedAt()).isNotNull();

        found.setName("Meera Nair");
        userRepository.saveAndFlush(found);
        assertThat(userRepository.findById(saved.getId()).orElseThrow().getName()).isEqualTo("Meera Nair");

        userRepository.deleteById(saved.getId());
        entityManager.flush();
        assertThat(userRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void persistsUserWithRolesAndAddresses() {
        Role customer = roleRepository.findByRoleName("CUSTOMER")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setRoleName("CUSTOMER");
                    return roleRepository.save(role);
                });

        User user = user("Arjun Das", "arjun@shopsphere.test");
        user.getRoles().add(customer);

        Address home = new Address();
        home.setUser(user);
        home.setLine1("14 Residency Road");
        home.setCity("Bengaluru");
        home.setState("Karnataka");
        home.setPincode("560001");
        home.setCountry("India");
        home.setFullName("Arjun Das");
        home.setPhone("+91 9000000006");
        home.setDefaultAddress(true);
        user.getAddresses().add(home);

        userRepository.saveAndFlush(user);
        entityManager.clear();

        User found = userRepository.findByEmail("arjun@shopsphere.test").orElseThrow();
        assertThat(found.getRoles()).extracting(Role::getRoleName).containsExactly("CUSTOMER");
        assertThat(addressRepository.findByUserId(found.getId()))
                .extracting(Address::getCity)
                .containsExactly("Bengaluru");
    }

    @Test
    void persistsProductWithOneInventoryRowAndCategory() {
        User seller = user("Riya Shah", "riya@shopsphere.test");
        userRepository.save(seller);

        Category electronics = category("Electronics", null);
        Category phones = category("Phones", electronics);
        categoryRepository.save(electronics);
        categoryRepository.save(phones);

        Product product = product(seller, phones, "Nova X1 Phone", "PHN-001", "19999.00");
        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setStockQuantity(25);
        product.setInventory(inventory);
        productRepository.saveAndFlush(product);
        entityManager.clear();

        Product found = productRepository.findByCategoryId(phones.getId()).getFirst();
        Inventory stock = inventoryRepository.findByProductId(found.getId()).orElseThrow();
        assertThat(found.getPrice()).isEqualByComparingTo("19999.00");
        assertThat(stock.getStockQuantity()).isEqualTo(25);
        assertThat(stock.getProduct().getId()).isEqualTo(found.getId());
    }

    @Test
    void persistsCartLineAndWishlistItem() {
        User customer = user("Meera Iyer", "buyer@shopsphere.test");
        userRepository.save(customer);
        Product product = savedProduct("PHN-010", "14999.00");

        Cart cart = new Cart();
        cart.setUser(customer);
        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        cart.getItems().add(cartItem);
        cartRepository.saveAndFlush(cart);

        Wishlist wishlist = new Wishlist();
        wishlist.setUser(customer);
        WishlistItem wishlistItem = new WishlistItem();
        wishlistItem.setWishlist(wishlist);
        wishlistItem.setProduct(product);
        wishlist.getItems().add(wishlistItem);
        wishlistRepository.saveAndFlush(wishlist);
        entityManager.clear();

        assertThat(cartItemRepository.findByCartId(cart.getId()))
                .singleElement()
                .extracting(CartItem::getQuantity)
                .isEqualTo(2);
        assertThat(wishlistItemRepository.findByWishlistId(wishlist.getId())).hasSize(1);
    }

    @Test
    void orderItemKeepsSnapshottedPriceAfterCatalogChange() {
        User customer = user("Arjun Das", "orders@shopsphere.test");
        userRepository.save(customer);
        Product product = savedProduct("LAP-001", "54999.00");

        Order order = new Order();
        order.setUser(customer);
        order.setStatus(OrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("54999.00"));
        order.setShippingAddressLine1("12 MG Road");
        order.setShippingCity("Bengaluru");
        order.setShippingState("Karnataka");
        order.setShippingPincode("560001");
        order.setShippingCountry("India");

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setUnitPrice(new BigDecimal("54999.00"));
        item.setQuantity(1);
        item.setLineTotal(new BigDecimal("54999.00"));
        order.getItems().add(item);
        orderRepository.saveAndFlush(order);

        product.setPrice(new BigDecimal("57999.00"));
        productRepository.saveAndFlush(product);
        entityManager.clear();

        OrderItem snapshot = orderItemRepository.findById(item.getId()).orElseThrow();
        Product current = productRepository.findById(product.getId()).orElseThrow();
        assertThat(snapshot.getProductName()).isEqualTo("AeroBook 14");
        assertThat(snapshot.getUnitPrice()).isEqualByComparingTo("54999.00");
        assertThat(current.getPrice()).isEqualByComparingTo("57999.00");
    }

    @Test
    void savesPaymentForOrder() {
        User customer = user("Meera Iyer", "pay@shopsphere.test");
        userRepository.save(customer);
        Product product = savedProduct("HOM-001", "1899.00");

        Order order = new Order();
        order.setUser(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("1899.00"));
        order.setShippingAddressLine1("8 Anna Salai");
        order.setShippingCity("Chennai");
        order.setShippingState("Tamil Nadu");
        order.setShippingPincode("600002");
        order.setShippingCountry("India");

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(1);
        item.setLineTotal(product.getPrice());
        order.getItems().add(item);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("1899.00"));
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentMethod("UPI");
        payment.setTransactionRef("TXN-TEST-1");
        order.getPayments().add(payment);
        orderRepository.saveAndFlush(order);
        entityManager.clear();

        Payment stored = paymentRepository.findByOrderId(order.getId()).getFirst();
        assertThat(stored.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(stored.getAmount()).isEqualByComparingTo("1899.00");
        assertThat(stored.getTransactionRef()).isEqualTo("TXN-TEST-1");
    }

    private User user(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(PASSWORD_HASH);
        return user;
    }

    private Category category(String name, Category parent) {
        Category category = new Category();
        category.setName(name);
        category.setParent(parent);
        return category;
    }

    private Product product(User seller, Category category, String name, String sku, String price) {
        Product product = new Product();
        product.setSeller(seller);
        product.setCategory(category);
        product.setName(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal(price));
        product.setActive(true);
        return product;
    }

    private Product savedProduct(String sku, String price) {
        User seller = user("Kabir Menon", "seller-" + sku + "@shopsphere.test");
        userRepository.save(seller);
        Category category = category(sku + "-cat", null);
        categoryRepository.save(category);
        Product product = product(seller, category, "AeroBook 14", sku, price);
        return productRepository.saveAndFlush(product);
    }
}
