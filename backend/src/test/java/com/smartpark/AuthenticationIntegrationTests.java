package com.smartpark;

import com.smartpark.dto.auth.LoginRequest;
import com.smartpark.dto.auth.RegisterRequest;
import com.smartpark.entity.Role;
import com.smartpark.entity.RoleType;
import com.smartpark.entity.User;
import com.smartpark.repository.RoleRepository;
import com.smartpark.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("1. Successful user registration assigns DRIVER role and hashes password")
    void testSuccessfulRegistration() throws Exception {
        String json = """
            {
                "firstName": "Vikram",
                "lastName": "Aditya",
                "email": "vikram.aditya@example.in",
                "phoneNumber": "+919876500001",
                "password": "SecurePassword123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is("vikram.aditya@example.in")))
                .andExpect(jsonPath("$.role", is("DRIVER")))
                .andExpect(jsonPath("$.active", is(true)))
                // Verify passwordHash is NEVER exposed in the API response
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        User user = userRepository.findByEmail("vikram.aditya@example.in").orElseThrow();
        assertThat(user.getRole().getName()).isEqualTo(RoleType.DRIVER);
        assertThat(user.getPasswordHash()).isNotEqualTo("SecurePassword123");
        assertThat(passwordEncoder.matches("SecurePassword123", user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("2. Registration fails with 400 Bad Request on invalid email")
    void testRegistrationInvalidEmail() throws Exception {
        String json = """
            {
                "firstName": "Invalid",
                "lastName": "Email",
                "email": "not-an-email",
                "phoneNumber": "+919876500002",
                "password": "Password123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.fieldErrors.email", notNullValue()));
    }

    @Test
    @DisplayName("3. Registration fails with 400 Bad Request on short password (<8 chars)")
    void testRegistrationShortPassword() throws Exception {
        String json = """
            {
                "firstName": "Short",
                "lastName": "Password",
                "email": "short.pwd@example.in",
                "phoneNumber": "+919876500003",
                "password": "short"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    @Test
    @DisplayName("4. Registration rejects duplicate email with 409 Conflict")
    void testRegistrationDuplicateEmail() throws Exception {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        userRepository.saveAndFlush(new User("First", "User", "dup.email@example.in", "+919876500004", passwordEncoder.encode("Pass1234"), driverRole));

        String json = """
            {
                "firstName": "Second",
                "lastName": "User",
                "email": "dup.email@example.in",
                "phoneNumber": "+919876500005",
                "password": "Password123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("5. Registration rejects duplicate phone number with 409 Conflict")
    void testRegistrationDuplicatePhone() throws Exception {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        userRepository.saveAndFlush(new User("UserA", "Test", "userA@example.in", "+919876500006", passwordEncoder.encode("Pass1234"), driverRole));

        String json = """
            {
                "firstName": "UserB",
                "lastName": "Test",
                "email": "userB@example.in",
                "phoneNumber": "+919876500006",
                "password": "Password123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")));
    }

    @Test
    @DisplayName("6. Successful login returns JWT access token and safe claims")
    void testSuccessfulLogin() throws Exception {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        userRepository.saveAndFlush(new User("Deepak", "Sharma", "deepak.sharma@example.in", "+919876500007", passwordEncoder.encode("SecretPassword123"), driverRole));

        String json = """
            {
                "email": "deepak.sharma@example.in",
                "password": "SecretPassword123"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.role", is("DRIVER")))
                .andExpect(jsonPath("$.email", is("deepak.sharma@example.in")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("7. Login with wrong password returns 401 Unauthorized with generic message")
    void testLoginWrongPassword() throws Exception {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        userRepository.saveAndFlush(new User("Test", "User", "wrongpwd@example.in", "+919876500008", passwordEncoder.encode("CorrectPassword"), driverRole));

        String json = """
            {
                "email": "wrongpwd@example.in",
                "password": "WrongPassword123"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("8. Login with non-existent email returns generic 401 (prevent enumeration)")
    void testLoginNonExistentEmail() throws Exception {
        String json = """
            {
                "email": "nonexistent.user@example.in",
                "password": "AnyPassword123"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("9. Inactive user is rejected during login with 401 Unauthorized")
    void testInactiveUserLoginRejected() throws Exception {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        User inactiveUser = new User("Inactive", "User", "inactive@example.in", "+919876500009", passwordEncoder.encode("Pass1234"), driverRole);
        inactiveUser.setActive(false);
        userRepository.saveAndFlush(inactiveUser);

        String json = """
            {
                "email": "inactive@example.in",
                "password": "Pass1234"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("10. Unauthenticated access to protected endpoint returns 401 Unauthorized")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/security/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("11. Public health endpoint remains accessible without authentication")
    void testPublicHealthEndpointAccessible() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", is("smartpark-backend")));
    }

    @Test
    @DisplayName("12. Malicious public registration payload with role=ADMIN still creates DRIVER account")
    void testMaliciousRegistrationRoleAdminIgnored() throws Exception {
        String json = """
            {
                "firstName": "Attacker",
                "lastName": "Admin",
                "email": "attacker.admin@example.in",
                "phoneNumber": "+919876500010",
                "password": "Password123",
                "role": "ADMIN"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("attacker.admin@example.in")))
                .andExpect(jsonPath("$.role", is("DRIVER")));

        User user = userRepository.findByEmail("attacker.admin@example.in").orElseThrow();
        assertThat(user.getRole().getName()).isEqualTo(RoleType.DRIVER);
        assertThat(user.getRole().getName()).isNotEqualTo(RoleType.ADMIN);
    }

    @Test
    @DisplayName("13. Malicious public registration payload with role=OPERATOR still creates DRIVER account")
    void testMaliciousRegistrationRoleOperatorIgnored() throws Exception {
        String json = """
            {
                "firstName": "Attacker",
                "lastName": "Operator",
                "email": "attacker.op@example.in",
                "phoneNumber": "+919876500011",
                "password": "Password123",
                "role": "OPERATOR"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("attacker.op@example.in")))
                .andExpect(jsonPath("$.role", is("DRIVER")));

        User user = userRepository.findByEmail("attacker.op@example.in").orElseThrow();
        assertThat(user.getRole().getName()).isEqualTo(RoleType.DRIVER);
        assertThat(user.getRole().getName()).isNotEqualTo(RoleType.OPERATOR);
    }
}
