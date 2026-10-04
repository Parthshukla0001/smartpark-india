package com.smartpark.service;

import com.smartpark.dto.auth.AuthResponse;
import com.smartpark.dto.auth.LoginRequest;
import com.smartpark.dto.auth.RegisterRequest;
import com.smartpark.dto.user.UserResponse;
import com.smartpark.entity.Role;
import com.smartpark.entity.RoleType;
import com.smartpark.entity.User;
import com.smartpark.exception.AccountLockedException;
import com.smartpark.exception.AuthenticationFailedException;
import com.smartpark.exception.DuplicateResourceException;
import com.smartpark.repository.RoleRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.jwt.JwtService;
import com.smartpark.security.service.LoginAttemptService;
import com.smartpark.security.service.SecurityAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final SecurityAuditLogger auditLogger;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptService loginAttemptService,
            SecurityAuditLogger auditLogger
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Enforce uniqueness pre-checks
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.REGISTRATION_FAILURE,
                    request.getEmail(), "Email already registered");
            throw new DuplicateResourceException("An account with this email address already exists");
        }

        if (request.getPhoneNumber() != null && userRepository.findByPhoneNumber(request.getPhoneNumber().trim()).isPresent()) {
            auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.REGISTRATION_FAILURE,
                    request.getEmail(), "Phone number already registered");
            throw new DuplicateResourceException("An account with this phone number already exists");
        }

        // Public registration assigns DRIVER role by default; client cannot elevate to ADMIN or OPERATOR
        Role driverRole = roleRepository.findByName(RoleType.DRIVER)
                .orElseThrow(() -> new IllegalStateException("Default DRIVER role is missing from database"));

        // Hash password securely with BCrypt
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                request.getEmail().trim().toLowerCase(),
                request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null,
                hashedPassword,
                driverRole
        );

        try {
            User savedUser = userRepository.save(user);
            auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.REGISTRATION_SUCCESS,
                    savedUser.getEmail(), "Driver account registered successfully");
            return UserResponse.fromEntity(savedUser);
        } catch (DataIntegrityViolationException ex) {
            // Database-level uniqueness constraint safeguard against race conditions
            auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.REGISTRATION_FAILURE,
                    request.getEmail(), "Database constraint violation during registration");
            throw new DuplicateResourceException("Account registration conflict: email or phone number already in use");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // 1. Check brute-force lockout status
        if (loginAttemptService.isLocked(email)) {
            auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.ACCOUNT_LOCKED,
                    email, "Login attempt on locked account rejected");
            throw new AccountLockedException("Too many failed login attempts. Account temporarily locked. Please try again later.");
        }

        // 2. Fetch user by email
        User user = userRepository.findByEmail(email).orElse(null);

        // 3. Prevent user enumeration: generic error message if user not found, inactive, or bad password
        if (user == null || !user.isActive() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailedAttempt(email);
            auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.LOGIN_FAILURE,
                    email, "Invalid credentials or inactive user");
            throw new AuthenticationFailedException("Invalid email or password");
        }

        // 4. Successful authentication - clear failure record
        loginAttemptService.recordSuccess(email);
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.LOGIN_SUCCESS,
                email, "User logged in successfully");

        // 5. Generate signed JWT token
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().getName().name());

        return new AuthResponse(
                token,
                jwtService.getExpirationMs(),
                user.getId(),
                user.getEmail(),
                user.getRole().getName().name(),
                user.getFirstName(),
                user.getLastName()
        );
    }
}
