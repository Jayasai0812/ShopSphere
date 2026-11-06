package com.shopsphere;

import com.shopsphere.entity.Role;
import com.shopsphere.entity.User;
import com.shopsphere.repository.RoleRepository;
import com.shopsphere.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-014 login acceptance tests. Uses MockMvc so status codes reflect the
 * full security + exception-handling pipeline. Unique emails per test guard
 * against cross-test interference on the shared in-memory schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final String PASSWORD = "Password1";

    /** Cache of test users created directly through the repository. */
    private final Map<String, User> seeded = new ConcurrentHashMap<>();

    /**
     * Seeds a user directly via the repository so tests can control the exact
     * roles and password hash — registration would always add CUSTOMER.
     */
    private User seedUser(String email, String password, String... roleNames) {
        return seeded.computeIfAbsent(email, e -> transactionTemplate.execute(status -> {
            User user = new User();
            user.setName("Test");
            user.setEmail(e);
            user.setPasswordHash(passwordEncoder.encode(password));
            for (String roleName : roleNames) {
                Role role = roleRepository.findByRoleName(roleName)
                        .orElseGet(() -> {
                            Role r = new Role();
                            r.setRoleName(roleName);
                            return roleRepository.save(r);
                        });
                user.getRoles().add(role);
            }
            return userRepository.save(user);
        }));
    }

    private String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    /** Registers a standard customer through the real endpoint. */
    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Jane","email":"%s","password":"%s","phone":null}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    @Test
    void validCustomerLoginReturns200() throws Exception {
        register("login-jane@example.com");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("login-jane@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value("login-jane@example.com"))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void validAdminLoginReturns200() throws Exception {
        seedUser("login-admin@example.com", PASSWORD, "ADMIN");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("login-admin@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        register("login-wrongpw@example.com");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("login-wrongpw@example.com", "WrongPass1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("nobody@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailAndWrongPasswordReturnSamePublicError() throws Exception {
        register("login-real@example.com");

        MvcResult unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("ghost@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("login-real@example.com", "WrongPass1")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String a = unknownEmail.getResponse().getContentAsString();
        String b = wrongPassword.getResponse().getContentAsString();
        System.out.println("UNKNOWN-EMAIL status=" + unknownEmail.getResponse().getStatus()
                + " body=[" + a + "]");
        System.out.println("WRONG-PASS status=" + wrongPassword.getResponse().getStatus()
                + " body=[" + b + "]");
        // Same public detail, no leak of whether the account exists. Global
        // ProblemDetail JSON includes type/title/status/detail/timestamp.
        assertThat(a).contains("Invalid email or password");
        assertThat(b).isEqualTo(a);
    }

    @Test
    void userWithNoRolesReturns401() throws Exception {
        seedUser("login-norole@example.com", PASSWORD /* no roles */);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("login-norole@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginEmailIsCaseInsensitive() throws Exception {
        register("login-case@example.com");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("LOGIN-CASE@EXAMPLE.COM", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void loginResponseNeverExposesPasswordOrHash() throws Exception {
        register("login-nosecret@example.com");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("login-nosecret@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD);
        assertThat(body).doesNotContain("$2a$");
        assertThat(body).doesNotContain("passwordHash");
    }

    /**
     * The critical end-to-end check: the token issued by /auth/login must
     * actually authenticate a request to a protected endpoint through the
     * real JwtAuthenticationFilter (not the test security context).
     */
    @Test
    void returnedJwtAuthenticatesProtectedEndpoint() throws Exception {
        register("login-jwtflow@example.com");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("login-jwtflow@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(responseBody, "$.token");

        // GET /api/v1/cart is authenticated-only and resolves the current user
        // through the JWT filter + CurrentUserProvider.
        mockMvc.perform(get("/api/v1/cart")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        // Without the token the same endpoint must be 401.
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("blankpw@example.com", "")))
                .andExpect(status().isBadRequest());
    }
}
