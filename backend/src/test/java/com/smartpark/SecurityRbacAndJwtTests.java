package com.smartpark;

import com.smartpark.dto.auth.LoginRequest;
import com.smartpark.entity.Role;
import com.smartpark.entity.RoleType;
import com.smartpark.entity.User;
import com.smartpark.repository.RoleRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.jwt.JwtService;
import com.smartpark.security.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityRbacAndJwtTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    private User driverUser;
    private User operatorUser;
    private User adminUser;

    private String driverToken;
    private String operatorToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        Role operatorRole = roleRepository.findByName(RoleType.OPERATOR).orElseThrow();
        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseThrow();

        driverUser = userRepository.saveAndFlush(new User("Rahul", "Driver", "driver.test@example.in", "+919876599001", passwordEncoder.encode("Password123"), driverRole));
        operatorUser = userRepository.saveAndFlush(new User("Kavita", "Operator", "operator.test@example.in", "+919876599002", passwordEncoder.encode("Password123"), operatorRole));
        adminUser = userRepository.saveAndFlush(new User("Sanjay", "Admin", "admin.test@example.in", "+919876599003", passwordEncoder.encode("Password123"), adminRole));

        driverToken = jwtService.generateToken(driverUser.getId(), driverUser.getEmail(), RoleType.DRIVER.name());
        operatorToken = jwtService.generateToken(operatorUser.getId(), operatorUser.getEmail(), RoleType.OPERATOR.name());
        adminToken = jwtService.generateToken(adminUser.getId(), adminUser.getEmail(), RoleType.ADMIN.name());
    }

    @Test
    @DisplayName("1. JWT generation extracts correct userId, email, and role")
    void testJwtClaims() {
        String token = jwtService.generateToken(driverUser.getId(), driverUser.getEmail(), "DRIVER");
        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo(driverUser.getEmail());
        assertThat(jwtService.extractUserId(token)).isEqualTo(driverUser.getId());
        assertThat(jwtService.extractRole(token)).isEqualTo("DRIVER");
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("2. Tampered JWT token signature is rejected")
    void testTamperedTokenRejected() throws Exception {
        String tamperedToken = driverToken.substring(0, driverToken.length() - 6) + "abcdef";

        mockMvc.perform(get("/api/security/me")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("3. DRIVER role can access /api/security/me and /api/security/driver")
    void testDriverAccessAllowed() throws Exception {
        mockMvc.perform(get("/api/security/me")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(driverUser.getEmail())))
                .andExpect(jsonPath("$.role", is("DRIVER")));

        mockMvc.perform(get("/api/security/driver")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access", is("GRANTED")));
    }

    @Test
    @DisplayName("4. DRIVER role is forbidden from /api/security/admin with 403 Forbidden")
    void testDriverForbiddenFromAdmin() throws Exception {
        mockMvc.perform(get("/api/security/admin")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")));
    }

    @Test
    @DisplayName("5. OPERATOR role can access /api/security/operator but is forbidden from /api/security/admin")
    void testOperatorRbac() throws Exception {
        mockMvc.perform(get("/api/security/operator")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access", is("GRANTED")));

        mockMvc.perform(get("/api/security/admin")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("6. ADMIN role can access /api/security/admin, /driver, and /operator")
    void testAdminElevatedAccess() throws Exception {
        mockMvc.perform(get("/api/security/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access", is("GRANTED")));

        mockMvc.perform(get("/api/security/driver")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access", is("GRANTED")));

        mockMvc.perform(get("/api/security/operator")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access", is("GRANTED")));
    }

    @Test
    @DisplayName("7. In-memory brute-force protection blocks user after 5 failed attempts")
    void testBruteForceLockout() throws Exception {
        String targetEmail = driverUser.getEmail();
        loginAttemptService.reset(targetEmail);

        String badLoginJson = String.format("""
            {
                "email": "%s",
                "password": "WrongPassword"
            }
            """, targetEmail);

        // Perform 5 failed attempts
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badLoginJson))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt should now be locked and return 429 Too Many Requests
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLoginJson))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.message", containsString("temporarily locked")));

        // Cleanup
        loginAttemptService.reset(targetEmail);
    }

    @Test
    @DisplayName("8. Successful login clears failed attempt counters")
    void testSuccessfulLoginResetsAttempts() throws Exception {
        String targetEmail = driverUser.getEmail();
        loginAttemptService.reset(targetEmail);

        String badLoginJson = String.format("""
            {
                "email": "%s",
                "password": "WrongPassword"
            }
            """, targetEmail);

        // Perform 2 failed attempts
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badLoginJson))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(loginAttemptService.getRemainingAttempts(targetEmail)).isEqualTo(3);

        // Perform successful login
        String goodLoginJson = String.format("""
            {
                "email": "%s",
                "password": "Password123"
            }
            """, targetEmail);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodLoginJson))
                .andExpect(status().isOk());

        // Counter is reset
        assertThat(loginAttemptService.getRemainingAttempts(targetEmail)).isEqualTo(5);
    }
}
