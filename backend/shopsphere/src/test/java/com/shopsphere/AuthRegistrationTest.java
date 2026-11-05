package com.shopsphere;

import com.shopsphere.dto.RegisterRequestDTO;
import com.shopsphere.entity.Cart;
import com.shopsphere.entity.User;
import com.shopsphere.entity.Wishlist;
import com.shopsphere.repository.CartRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.repository.WishlistRepository;
import com.shopsphere.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-013.2 registration acceptance tests, run through MockMvc so status
 * codes reflect the full security + exception-handling pipeline. Unique
 * email per test guards against cross-test interference.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthRegistrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final String PASSWORD = "Password1";

    private String body(String email, String password, String name, String phone) {
        return """
                {"name":%s,"email":"%s","password":"%s","phone":%s}
                """.formatted(name == null ? null : "\"" + name + "\"", email,
                password, phone == null ? null : "\"" + phone + "\"");
    }

    private MvcResult register(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
    }

    @Test
    void registerValidCustomerReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("jane@example.com", PASSWORD, "Jane Doe", "+91 9876543210")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerDuplicateEmailReturns409() throws Exception {
        register(body("dup@example.com", PASSWORD, "First", null));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup@example.com", PASSWORD, "Second", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void registerSameEmailDifferentCaseReturns409() throws Exception {
        register(body("case@example.com", PASSWORD, "First", null));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CASE@EXAMPLE.COM", PASSWORD, "Second", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-an-email", PASSWORD, "Jane", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordTooShortReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("shortpw@example.com", "Ab1", "Jane", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("noname@example.com", PASSWORD, null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankPhoneAcceptedAsNull() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("blankphone@example.com", PASSWORD, "Jane", "")))
                .andExpect(status().isCreated());
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByEmail("blankphone@example.com").orElseThrow();
            assertThat(user.getPhone()).isNull();
        });
    }

    @Test
    @Transactional
    void passwordStoredAsBcryptHash() {
        authService.register(new RegisterRequestDTO("Hash", "hash@example.com", PASSWORD, null));
        User user = userRepository.findByEmail("hash@example.com").orElseThrow();
        assertThat(user.getPasswordHash())
                .startsWith("$2a$")
                .doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    @Transactional
    void newUserHasOnlyCustomerRole() {
        authService.register(new RegisterRequestDTO("Roles", "roles@example.com", PASSWORD, null));
        User user = userRepository.findByEmail("roles@example.com").orElseThrow();
        assertThat(user.getRoles())
                .extracting(r -> r.getRoleName())
                .containsExactly("CUSTOMER");
    }

    @Test
    @Transactional
    void cartAndWishlistCreated() {
        authService.register(new RegisterRequestDTO("Cart", "cart@example.com", PASSWORD, null));
        User user = userRepository.findByEmail("cart@example.com").orElseThrow();
        Cart cart = cartRepository.findAll().stream()
                .filter(c -> c.getUser() != null && user.getId().equals(c.getUser().getId()))
                .findFirst().orElse(null);
        assertThat(cart).isNotNull();
        Wishlist wishlist = wishlistRepository.findAll().stream()
                .filter(w -> w.getUser() != null && user.getId().equals(w.getUser().getId()))
                .findFirst().orElse(null);
        assertThat(wishlist).isNotNull();
    }

    @Test
    void failedRegistrationLeavesNoPartialRecords() throws Exception {
        register(body("rollback@example.com", PASSWORD, "First", null));
        long usersBefore = userRepository.count();
        long cartsBefore = cartRepository.count();
        long wishlistsBefore = wishlistRepository.count();

        MvcResult response = register(body("rollback@example.com", PASSWORD, "Second", null));
        assertThat(response.getResponse().getStatus()).isEqualTo(409);

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(cartRepository.count()).isEqualTo(cartsBefore);
        assertThat(wishlistRepository.count()).isEqualTo(wishlistsBefore);
    }

    @Test
    void clientCannotAssignAdminRole() throws Exception {
        // The DTO has no role field at all — unknown JSON properties are
        // ignored, so an injected "role":"ADMIN" must have zero effect.
        String json = """
                {"name":"Evil","email":"evil@example.com","password":"Password1",
                 "phone":null,"role":"ADMIN","roles":["ADMIN"],"isAdmin":true}
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByEmail("evil@example.com").orElseThrow();
            assertThat(user.getRoles())
                    .extracting(r -> r.getRoleName())
                    .containsExactly("CUSTOMER");
        });
    }
}
