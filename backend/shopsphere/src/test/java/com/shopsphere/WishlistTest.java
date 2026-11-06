package com.shopsphere;

import com.jayway.jsonpath.JsonPath;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.InventoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.repository.WishlistItemRepository;
import com.shopsphere.repository.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-017 wishlist acceptance tests. Every request is authenticated with a
 * real JWT from /auth/login, so the tests exercise the full filter +
 * CurrentUserProvider pipeline. Unique emails/SKUs per test guard against
 * cross-test interference on the shared in-memory schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WishlistTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private InventoryRepository inventoryRepository;

        @Autowired
        private WishlistRepository wishlistRepository;

        @Autowired
        private WishlistItemRepository wishlistItemRepository;

        private static final String PASSWORD = "Password1";

        /** Registers a user and returns a valid Bearer token for them. */
        private String registerAndLogin(String email) throws Exception {
                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Jane\",\"email\":\"%s\",\"password\":\"%s\",\"phone\":null}"
                                                .formatted(email, PASSWORD)))
                                .andExpect(status().isCreated());

                MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                                .andExpect(status().isOk())
                                .andReturn();
                return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        }

        /** Creates an active product with stock and returns its id. */
        private long createProduct(String sku, boolean active, int stock) {
                User seller = userRepository.findByEmail("wishlist-seller@shopsphere.test")
                                .orElseGet(() -> {
                                        User s = new User();
                                        s.setName("Wishlist Seller");
                                        s.setEmail("wishlist-seller@shopsphere.test");
                                        s.setPasswordHash("x");
                                        return userRepository.save(s);
                                });
                Category category = categoryRepository.findByNameIgnoreCase("Wishlist Test Category")
                                .orElseGet(() -> {
                                        Category c = new Category();
                                        c.setName("Wishlist Test Category");
                                        return categoryRepository.save(c);
                                });

                Product p = new Product();
                p.setName("Product " + sku);
                p.setSku(sku);
                p.setPrice(new BigDecimal("999.00"));
                p.setCategory(category);
                p.setSeller(seller);
                p.setActive(active);
                Inventory inv = new Inventory();
                inv.setProduct(p);
                inv.setStockQuantity(stock);
                p.setInventory(inv);
                return productRepository.save(p).getId();
        }

        /** Adds a product to the caller's wishlist and returns the item id. */
        private long addItem(String token, long productId) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/v1/users/me/wishlist/items")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":%d}".formatted(productId)))
                                .andExpect(status().isCreated())
                                .andReturn();
                return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.itemId")).longValue();
        }

        @Test
        void emptyWishlistReturnsEmptyList() throws Exception {
                String token = registerAndLogin("wl-empty@example.com");

                mockMvc.perform(get("/api/v1/users/me/wishlist")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                // Wishlist is created eagerly at registration, so
                                // it always exists with zero items for a new user.
                                .andExpect(jsonPath("$.wishlistId").isNumber())
                                .andExpect(jsonPath("$.items.length()").value(0));
        }

        @Test
        void addAndListSavedProducts() throws Exception {
                String token = registerAndLogin("wl-add@example.com");
                long p1 = createProduct("WL-SKU-001", true, 10);
                long p2 = createProduct("WL-SKU-002", true, 0);

                long item1 = addItem(token, p1);
                long item2 = addItem(token, p2);

                MvcResult list = mockMvc.perform(get("/api/v1/users/me/wishlist")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.wishlistId").isNumber())
                                .andExpect(jsonPath("$.items.length()").value(2))
                                .andReturn();

                // Deterministic oldest-first ordering.
                assertThat(((java.util.List<?>) JsonPath.read(list.getResponse().getContentAsString(), "$.items[*].itemId"))
                                .stream().map(n -> ((Number) n).longValue()).toList())
                                .containsExactly(item1, item2);

                // p2 has stock 0 → available=false; p1 has stock 10 → true.
                mockMvc.perform(get("/api/v1/users/me/wishlist")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(jsonPath("$.items[0].productId").value((int) p1))
                                .andExpect(jsonPath("$.items[0].available").value(true))
                                .andExpect(jsonPath("$.items[1].productId").value((int) p2))
                                .andExpect(jsonPath("$.items[1].available").value(false));
        }

        @Test
        void duplicateAddReturns409() throws Exception {
                String token = registerAndLogin("wl-dup@example.com");
                long productId = createProduct("WL-SKU-003", true, 5);

                addItem(token, productId);

                mockMvc.perform(post("/api/v1/users/me/wishlist/items")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":%d}".formatted(productId)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.detail").value("Product is already in the wishlist"));
        }

        @Test
        void removeItemReturns204AndItDisappears() throws Exception {
                String token = registerAndLogin("wl-remove@example.com");
                long productId = createProduct("WL-SKU-004", true, 5);
                long itemId = addItem(token, productId);

                mockMvc.perform(delete("/api/v1/users/me/wishlist/items/" + itemId)
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get("/api/v1/users/me/wishlist")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(jsonPath("$.items.length()").value(0));

                // Re-adding after removal is allowed (no lingering duplicate).
                addItem(token, productId);
        }

        @Test
        void unknownItemAndUnknownProductReturn404() throws Exception {
                String token = registerAndLogin("wl-404@example.com");

                mockMvc.perform(delete("/api/v1/users/me/wishlist/items/99999")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isNotFound());

                mockMvc.perform(post("/api/v1/users/me/wishlist/items")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":99999}"))
                                .andExpect(status().isNotFound());
        }

        @Test
        void withoutTokenReturns401() throws Exception {
                mockMvc.perform(get("/api/v1/users/me/wishlist"))
                                .andExpect(status().isUnauthorized());

                mockMvc.perform(post("/api/v1/users/me/wishlist/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":1}"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void invalidProductIdFailsValidation() throws Exception {
                String token = registerAndLogin("wl-validation@example.com");

                // Missing productId.
                mockMvc.perform(post("/api/v1/users/me/wishlist/items")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isBadRequest());

                // Negative productId.
                mockMvc.perform(post("/api/v1/users/me/wishlist/items")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":-5}"))
                                .andExpect(status().isBadRequest());

                // Nothing was created.
                mockMvc.perform(get("/api/v1/users/me/wishlist")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(jsonPath("$.items.length()").value(0));
        }

        @Test
        void inactiveProductCannotBeSaved() throws Exception {
                String token = registerAndLogin("wl-inactive@example.com");
                long productId = createProduct("WL-SKU-005", false, 5);

                mockMvc.perform(post("/api/v1/users/me/wishlist/items")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":%d}".formatted(productId)))
                                .andExpect(status().isConflict());
        }

        /**
         * Ownership: another user's wishlist item must be unreadable
         * through list, un-removable, and must look like a 404 (not 403) so
         * the endpoint does not reveal the item's existence. The victim's
         * wishlist must be untouched afterwards.
         */
        @Test
        void anotherUsersWishlistItemIsNotAccessible() throws Exception {
                String attackerToken = registerAndLogin("wl-attacker@example.com");
                String victimToken = registerAndLogin("wl-victim@example.com");
                long productId = createProduct("WL-SKU-006", true, 5);
                long victimItemId = addItem(victimToken, productId);

                mockMvc.perform(delete("/api/v1/users/me/wishlist/items/" + victimItemId)
                                .header("Authorization", "Bearer " + attackerToken))
                                .andExpect(status().isNotFound());

                // Victim's item survived and still lists under the victim.
                mockMvc.perform(get("/api/v1/users/me/wishlist")
                                .header("Authorization", "Bearer " + victimToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].itemId").value((int) victimItemId))
                                .andExpect(jsonPath("$.items[0].productId").value((int) productId));

                // The attacker's wishlist does not contain the victim's item.
                mockMvc.perform(get("/api/v1/users/me/wishlist")
                                .header("Authorization", "Bearer " + attackerToken))
                                .andExpect(jsonPath("$.items.length()").value(0));
        }

        @Test
        void wishlistDataIsPersistedAcrossRequests() throws Exception {
                String token = registerAndLogin("wl-persist@example.com");
                long productId = createProduct("WL-SKU-007", true, 5);
                addItem(token, productId);

                long wishlistId = ((Number) JsonPath.read(
                                mockMvc.perform(get("/api/v1/users/me/wishlist")
                                                .header("Authorization", "Bearer " + token))
                                                .andReturn().getResponse().getContentAsString(),
                                "$.wishlistId")).longValue();

                // Direct repository check: the row and its item really exist.
                assertThat(wishlistRepository.findById(wishlistId)).isPresent();
                assertThat(wishlistItemRepository.findByWishlistIdOrderByIdAsc(wishlistId))
                                .hasSize(1)
                                .first()
                                .satisfies(item -> assertThat(item.getProduct().getId()).isEqualTo(productId));
        }
}
