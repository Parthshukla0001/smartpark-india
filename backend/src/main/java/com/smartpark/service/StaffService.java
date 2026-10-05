package com.smartpark.service;

import com.smartpark.dto.user.CreateStaffRequest;
import com.smartpark.dto.user.UserResponse;
import com.smartpark.entity.Role;
import com.smartpark.entity.RoleType;
import com.smartpark.entity.User;
import com.smartpark.exception.DuplicateResourceException;
import com.smartpark.exception.ResourceNotFoundException;
import com.smartpark.repository.RoleRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.service.CurrentUserService;
import com.smartpark.security.service.SecurityAuditLogger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StaffService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditLogger auditLogger;
    private final CurrentUserService currentUserService;

    public StaffService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            SecurityAuditLogger auditLogger,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllStaff() {
        return userRepository.findByRole_NameInOrderByCreatedAtDesc(List.of(RoleType.OPERATOR, RoleType.ADMIN))
                .stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    @Transactional
    public UserResponse createStaff(CreateStaffRequest request) {
        RoleType targetRoleType = request.getRole() != null ? request.getRole() : RoleType.OPERATOR;
        if (targetRoleType == RoleType.DRIVER) {
            throw new IllegalArgumentException("Staff accounts cannot be assigned the DRIVER role. Use public customer registration instead.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An account with email " + normalizedEmail + " already exists");
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            String normalizedPhone = request.getPhoneNumber().trim();
            if (userRepository.findByPhoneNumber(normalizedPhone).isPresent()) {
                throw new DuplicateResourceException("An account with phone number " + normalizedPhone + " already exists");
            }
        }

        Role role = roleRepository.findByName(targetRoleType)
                .orElseThrow(() -> new IllegalStateException("Role " + targetRoleType + " is not configured in database"));

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User staff = new User(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                normalizedEmail,
                request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank() ? request.getPhoneNumber().trim() : null,
                hashedPassword,
                role
        );

        User savedStaff = userRepository.save(staff);
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.REGISTRATION_SUCCESS,
                savedStaff.getEmail(),
                "Staff account created by admin " + currentUserService.getCurrentUserEmail() + " with role " + targetRoleType);

        return UserResponse.fromEntity(savedStaff);
    }

    @Transactional
    public UserResponse toggleStaffStatus(Long staffId, boolean active) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with id: " + staffId));

        if (staff.getRole().getName() == RoleType.DRIVER) {
            throw new IllegalArgumentException("Cannot manage non-staff user via staff management endpoint");
        }

        // Prevent admin from deactivating their own account
        if (staff.getId().equals(currentUserService.getCurrentUserId()) && !active) {
            throw new IllegalArgumentException("Admins cannot deactivate their own account");
        }

        staff.setActive(active);
        User updated = userRepository.save(staff);

        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.REGISTRATION_SUCCESS,
                updated.getEmail(),
                "Staff account status changed to active=" + active + " by admin " + currentUserService.getCurrentUserEmail());

        return UserResponse.fromEntity(updated);
    }
}
