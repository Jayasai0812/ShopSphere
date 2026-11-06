package com.shopsphere;

import com.jayway.jsonpath.JsonPath;
import com.shopsphere.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-015 profile acceptance tests. Every request is authenticated with a
 * real JWT obtained from /auth/login, so the tests exercise the full
 * filter + CurrentUserProvider pipeline. Unique emails per test guard
 * against cross-test interference on the shared in-memory schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserProfileTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        private static final String PASSWORD = "Password1";

        /** Registers a user and returns a valid Bearer token for them. */
        private String registerAndLogin(String email) throws Exception {
                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Jane","email":"%s","password":"%s","phone":null}
                                                """.formatted(email, PASSWORD)))
                                .andExpect(status().isCreated());

                MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                                .andExpect(status().isOk())
                                .andReturn();
                return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        }

        @Test
        void getOwnProfileReturns200() throws Exception {
                String token = registerAndLogin("profile-get@example.com");

                mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.email").value("profile-get@example.com"))
                                .andExpect(jsonPath("$.name").value("Jane"))
                                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                                .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void getProfileWithoutTokenReturns401() throws Exception {
                mockMvc.perform(get("/api/v1/users/me"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void updateOwnNameAndPhoneReturns200() throws Exception {
                String token = registerAndLogin("profile-update@example.com");

                mockMvc.perform(put("/api/v1/users/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Jane Updated","phone":"+91 9876543210"}
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("Jane Updated"))
                                .andExpect(jsonPath("$.phone").value("+91 9876543210"));

                // Persisted
                mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                                .andExpect(jsonPath("$.name").value("Jane Updated"))
                                .andExpect(jsonPath("$.phone").value("+91 9876543210"));
        }

        @Test
        void clearPhoneReturns200AndNull() throws Exception {
                String token = registerAndLogin("profile-clear@example.com");

                mockMvc.perform(put("/api/v1/users/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Jane\",\"phone\":\"\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.phone").doesNotExist());
        }

        @Test
        void blankNameReturns400() throws Exception {
                String token = registerAndLogin("profile-blank@example.com");

                mockMvc.perform(put("/api/v1/users/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\",\"phone\":\"123\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void wrongCurrentPasswordReturns400AndHashUnchanged() throws Exception {
                String token = registerAndLogin("profile-wrongpw@example.com");
                String hashBefore = userRepository.findByEmail("profile-wrongpw@example.com")
                                .orElseThrow().getPasswordHash();

                mockMvc.perform(patch("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"WrongPass1\",\"newPassword\":\"NewPass123\"}"))
                                .andExpect(status().isBadRequest());

                String hashAfter = userRepository.findByEmail("profile-wrongpw@example.com")
                                .orElseThrow().getPasswordHash();
                assertThat(hashAfter).isEqualTo(hashBefore);
        }

        @Test
        void changePasswordSucceeds() throws Exception {
                String token = registerAndLogin("profile-chpw@example.com");

                mockMvc.perform(patch("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"NewPass123\"}"
                                                .formatted(PASSWORD)))
                                .andExpect(status().isOk());

                // Old password no longer works
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"profile-chpw@example.com\",\"password\":\"Password1\"}"))
                                .andExpect(status().isUnauthorized());

                // New password works
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"profile-chpw@example.com\",\"password\":\"NewPass123\"}"))
                                .andExpect(status().isOk());

                // Stored value is a BCrypt hash, never the raw password
                String hash = userRepository.findByEmail("profile-chpw@example.com")
                                .orElseThrow().getPasswordHash();
                assertThat(hash).startsWith("$2");
                assertThat(hash).isNotEqualTo("NewPass123");
                assertThat(passwordEncoder.matches("NewPass123", hash)).isTrue();
        }

        /**
         * The security core of TASK-015: a supplied user ID must never be able
         * to reach another user's profile. There is no /users/{id} endpoint, a
         * forged "id" in the PUT body is ignored (identity comes from the JWT),
         * and the response always reflects the caller.
         */
        @Test
        void anotherUsersProfileNotAccessibleThroughSuppliedId() throws Exception {
                String attackerToken = registerAndLogin("profile-attacker@example.com");
                String victimToken = registerAndLogin("profile-victim@example.com");

                long victimId = ((Number) JsonPath.read(
                                mockMvc.perform(get("/api/v1/users/me")
                                                .header("Authorization", "Bearer " + victimToken))
                                                .andExpect(status().isOk())
                                                .andReturn().getResponse().getContentAsString(),
                                "$.id")).longValue();

                // No /users/{id} route exists — direct ID addressing is 404.
                mockMvc.perform(get("/api/v1/users/" + victimId)
                                .header("Authorization", "Bearer " + attackerToken))
                                .andExpect(status().isNotFound());

                // A forged id in the body is ignored: the update lands on the
                // attacker's own row and the response shows the attacker.
                MvcResult result = mockMvc.perform(put("/api/v1/users/me")
                                .header("Authorization", "Bearer " + attackerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"id\":" + victimId + ",\"name\":\"Hacked\",\"phone\":null}"))
                                .andExpect(status().isOk())
                                .andReturn();

                assertThat(((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id"))
                                .longValue()).isNotEqualTo(victimId);
                assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.email"))
                                .isEqualTo("profile-attacker@example.com");

                // Victim's record untouched.
                mockMvc.perform(get("/api/v1/users/me")
                                .header("Authorization", "Bearer " + victimToken))
                                .andExpect(jsonPath("$.name").value("Jane"))
                                .andExpect(jsonPath("$.email").value("profile-victim@example.com"));
        }
}
