package com.shopsphere;

import com.jayway.jsonpath.JsonPath;
import com.shopsphere.repository.AddressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-013 address management acceptance tests. Every request is
 * authenticated with a real JWT from /auth/login, so the tests exercise the
 * full filter + CurrentUserProvider pipeline. Unique emails per test guard
 * against cross-test interference on the shared in-memory schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AddressManagementTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private AddressRepository addressRepository;

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

        /** Creates one address for the token's user and returns the DTO id. */
        private long createAddress(String token, String line1, boolean isDefault) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"fullName":"Jane Doe","phone":"+91 9876543210",
                                                 "addressLine1":"%s","addressLine2":"Apt 4B",
                                                 "city":"Pune","state":"MH","postalCode":"411001",
                                                 "country":"India","addressType":"HOME","isDefault":%s}
                                                """.formatted(line1, isDefault)))
                                .andExpect(status().isCreated())
                                .andReturn();
                return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        }

        private static final String FULL_BODY = """
                        {"fullName":"Jane Doe","phone":"+91 9876543210",
                         "addressLine1":"MG Road 1","addressLine2":"Apt 4B",
                         "city":"Pune","state":"MH","postalCode":"411001",
                         "country":"India","addressType":"HOME"}
                        """;

        @Test
        void createFirstAddressBecomesDefaultAndReturns201() throws Exception {
                String token = registerAndLogin("addr-first@example.com");

                MvcResult result = mockMvc.perform(post("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                // Request explicitly asks for isDefault=false, but the
                                // first address must still become the default.
                                .content(FULL_BODY.replace("\"addressType\":\"HOME\"",
                                                "\"addressType\":\"HOME\",\"isDefault\":false")))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.isDefault").value(true))
                                .andExpect(jsonPath("$.addressLine1").value("MG Road 1"))
                                .andReturn();

                long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
                assertThat(id).isPositive();
        }

        @Test
        void listReturnsOnlyOwnAddresses() throws Exception {
                String token = registerAndLogin("addr-list@example.com");
                createAddress(token, "List Road 1", false);
                createAddress(token, "List Road 2", false);

                MvcResult result = mockMvc.perform(get("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn();
                List<?> items = JsonPath.read(result.getResponse().getContentAsString(), "$");
                assertThat(items).hasSize(2);
        }

        @Test
        void getWithoutTokenReturns401() throws Exception {
                mockMvc.perform(get("/api/v1/users/me/addresses"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void unknownAddressReturns404() throws Exception {
                String token = registerAndLogin("addr-404@example.com");
                mockMvc.perform(get("/api/v1/users/me/addresses/99999")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isNotFound());
        }

        /**
         * Ownership: another user's address must be unreadable, un-updatable
         * and undeletable, and it must look like a 404 (not 403) so the
         * endpoint does not leak the address's existence.
         */
        @Test
        void anotherUsersAddressIsNotAccessible() throws Exception {
                String attackerToken = registerAndLogin("addr-attacker@example.com");
                String victimToken = registerAndLogin("addr-victim@example.com");
                long victimAddressId = createAddress(victimToken, "Victim Road 1", false);

                mockMvc.perform(get("/api/v1/users/me/addresses/" + victimAddressId)
                                .header("Authorization", "Bearer " + attackerToken))
                                .andExpect(status().isNotFound());

                mockMvc.perform(patch("/api/v1/users/me/addresses/" + victimAddressId)
                                .header("Authorization", "Bearer " + attackerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"city\":\"Hacked\"}"))
                                .andExpect(status().isNotFound());

                mockMvc.perform(delete("/api/v1/users/me/addresses/" + victimAddressId)
                                .header("Authorization", "Bearer " + attackerToken))
                                .andExpect(status().isNotFound());

                mockMvc.perform(patch("/api/v1/users/me/addresses/" + victimAddressId + "/default")
                                .header("Authorization", "Bearer " + attackerToken))
                                .andExpect(status().isNotFound());

                // Victim's address survived untouched and unchanged.
                mockMvc.perform(get("/api/v1/users/me/addresses/" + victimAddressId)
                                .header("Authorization", "Bearer " + victimToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.city").value("Pune"))
                                .andExpect(jsonPath("$.addressLine1").value("Victim Road 1"));
        }

        /** PATCH is a partial update: omitted fields keep their value. */
        @Test
        void patchUpdatesOnlyProvidedFieldsAndEmptyLine2ClearsIt() throws Exception {
                String token = registerAndLogin("addr-patch@example.com");
                long id = createAddress(token, "Patch Road 1", false);

                mockMvc.perform(patch("/api/v1/users/me/addresses/" + id)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"city\":\"Mumbai\",\"addressLine2\":\"\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.city").value("Mumbai"))
                                .andExpect(jsonPath("$.addressLine2").doesNotExist())
                                // Everything not sent stayed as it was.
                                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                                .andExpect(jsonPath("$.addressLine1").value("Patch Road 1"))
                                .andExpect(jsonPath("$.postalCode").value("411001"))
                                .andExpect(jsonPath("$.state").value("MH"));

                // Explicit null also means "leave unchanged" for fullName.
                mockMvc.perform(patch("/api/v1/users/me/addresses/" + id)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\":null,\"state\":\"KA\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                                .andExpect(jsonPath("$.state").value("KA"));
        }

        /**
         * Default invariant: exactly one default per user. Creating a new
         * default un-sets the old one, and the switch happens atomically.
         */
        @Test
        void settingNewDefaultUnsetsPreviousOne() throws Exception {
                String token = registerAndLogin("addr-default@example.com");
                long first = createAddress(token, "Default Road 1", false);
                long second = createAddress(token, "Default Road 2", false);

                // Both creations with isDefault=false kept `first` as default.
                mockMvc.perform(get("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[?(@.id == " + first + ")].isDefault").value(true))
                                .andExpect(jsonPath("$[?(@.id == " + second + ")].isDefault").value(false));

                // Promote the second one explicitly.
                mockMvc.perform(patch("/api/v1/users/me/addresses/" + second + "/default")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isDefault").value(true));

                MvcResult list = mockMvc.perform(get("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn();
                String body = list.getResponse().getContentAsString();
                assertThat(((List<?>) JsonPath.read(body, "$[?(@.isDefault == true)].id"))
                                .stream().map(n -> ((Number) n).longValue()).toList())
                                .containsExactly(second);

                // PATCHing isDefault=true on the first also steals the flag.
                mockMvc.perform(patch("/api/v1/users/me/addresses/" + first)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"isDefault\":true}"))
                                .andExpect(status().isOk());

                MvcResult list2 = mockMvc.perform(get("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn();
                String body2 = list2.getResponse().getContentAsString();
                assertThat(((List<?>) JsonPath.read(body2, "$[?(@.isDefault == true)].id"))
                                .stream().map(n -> ((Number) n).longValue()).toList())
                                .containsExactly(first);
        }

        /** Deleting the default address promotes another remaining address. */
        @Test
        void deletingDefaultPromotesAnotherAddress() throws Exception {
                String token = registerAndLogin("addr-delete@example.com");
                long first = createAddress(token, "Delete Road 1", false);
                long second = createAddress(token, "Delete Road 2", false);

                mockMvc.perform(delete("/api/v1/users/me/addresses/" + first)
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isNoContent());

                // `second` is the only remaining address and becomes default.
                mockMvc.perform(get("/api/v1/users/me/addresses/" + second)
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isDefault").value(true));

                // Deleting the last address leaves zero addresses, no default.
                mockMvc.perform(delete("/api/v1/users/me/addresses/" + second)
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isNoContent());

                MvcResult list = mockMvc.perform(get("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn();
                assertThat((List<?>) JsonPath.read(list.getResponse().getContentAsString(), "$")).isEmpty();
                assertThat(addressRepository.count()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void validationRejectsMissingAndMalformedFields() throws Exception {
                String token = registerAndLogin("addr-validation@example.com");

                // Missing required fields (fullName, phone, addressLine1...)
                mockMvc.perform(post("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"city\":\"Pune\"}"))
                                .andExpect(status().isBadRequest());

                // Malformed postal code (digits only, 5-10).
                mockMvc.perform(post("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(FULL_BODY.replace("411001", "4110")))
                                .andExpect(status().isBadRequest());

                // Unknown addressType value.
                mockMvc.perform(post("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(FULL_BODY.replace("HOME", "CASTLE")))
                                .andExpect(status().isBadRequest());

                // PATCH validation: postalCode still enforced on partial update.
                long id = createAddress(token, "Validation Road 1", false);
                mockMvc.perform(patch("/api/v1/users/me/addresses/" + id)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"postalCode\":\"ABC\"}"))
                                .andExpect(status().isBadRequest());

                // Nothing was created by the failed requests.
                MvcResult list = mockMvc.perform(get("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn();
                assertThat((List<?>) JsonPath.read(list.getResponse().getContentAsString(), "$")).hasSize(1);
        }

        /**
         * Hardening (TASK-016 A): whitespace-only strings must be rejected even
 * though @Size counts spaces as characters, and phone values made only of
 * separators must fail. Applies to both create (POST) and update (PATCH).
 */
@Test
void whitespaceOnlyValuesAreRejected() throws Exception {
        String token = registerAndLogin("addr-ws@example.com");
        long id = createAddress(token, "Whitespace Road 1", false);

        // POST: whitespace-only required fields.
        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_BODY
                                        .replace("MG Road 1", "   ")
                                        .replace("Pune", "   ")
                                        .replace("Jane Doe", "  ")))
                        .andExpect(status().isBadRequest());

        // POST: whitespace-only country.
        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_BODY.replace("\"India\"", "\"   \"")))
                        .andExpect(status().isBadRequest());

        // PATCH: whitespace-only city rejected.
        mockMvc.perform(patch("/api/v1/users/me/addresses/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"   \"}"))
                        .andExpect(status().isBadRequest());

        // PATCH: whitespace-only fullName rejected.
        mockMvc.perform(patch("/api/v1/users/me/addresses/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"  \"}"))
                        .andExpect(status().isBadRequest());

        // PATCH: whitespace-only state and country rejected.
        mockMvc.perform(patch("/api/v1/users/me/addresses/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"\\t\",\"country\":\"\\n\"}"))
                        .andExpect(status().isBadRequest());

        // Nothing changed: the address still holds its original values.
        mockMvc.perform(get("/api/v1/users/me/addresses/" + id)
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.city").value("Pune"))
                        .andExpect(jsonPath("$.state").value("MH"));
}

/**
 * Hardening (TASK-016 A): phone must start AND end with a digit, so
 * values made only of hyphens/spaces — or separators at the edges — are
 * rejected on both create and update.
 */
@Test
void phoneMadeOnlyOfSeparatorsIsRejected() throws Exception {
        String token = registerAndLogin("addr-phone@example.com");
        long id = createAddress(token, "Phone Road 1", false);

        String[] badPhones = { "---------", "        ", "+---------", "1234-    ", "-1234567" };
        for (String bad : badPhones) {
                // POST
                mockMvc.perform(post("/api/v1/users/me/addresses")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(FULL_BODY.replace("+91 9876543210", bad)))
                                .andExpect(status().isBadRequest());
                // PATCH
                mockMvc.perform(patch("/api/v1/users/me/addresses/" + id)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"" + bad + "\"}"))
                                .andExpect(status().isBadRequest());
        }

        // A legitimate phone with separators still passes.
        mockMvc.perform(patch("/api/v1/users/me/addresses/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+91-98765 43210\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.phone").value("+91-98765 43210"));
}

/**
 * Hardening (TASK-016 C): listing is deterministically ordered
 * (oldest first), and deleting the default always promotes the oldest
 * remaining address — not an arbitrary database pick.
 */
@Test
void listingIsDeterministicAndDeletePromotesOldestRemaining() throws Exception {
        String token = registerAndLogin("addr-order@example.com");
        long first = createAddress(token, "Order Road 1", false);
        long second = createAddress(token, "Order Road 2", false);
        long third = createAddress(token, "Order Road 3", false);

        MvcResult list = mockMvc.perform(get("/api/v1/users/me/addresses")
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(((List<?>) JsonPath.read(list.getResponse().getContentAsString(), "$[*].id"))
                        .stream().map(n -> ((Number) n).longValue()).toList())
                        .containsExactly(first, second, third);

        // Delete the default (`first`) — `second` is the oldest remaining.
        mockMvc.perform(delete("/api/v1/users/me/addresses/" + first)
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/addresses/" + second)
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.isDefault").value(true));

        MvcResult list2 = mockMvc.perform(get("/api/v1/users/me/addresses")
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(((List<?>) JsonPath.read(list2.getResponse().getContentAsString(), "$[*].id"))
                        .stream().map(n -> ((Number) n).longValue()).toList())
                        .containsExactly(second, third);
        }
}
