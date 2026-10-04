package com.smartpark.controller;

import com.smartpark.dto.user.UserResponse;
import com.smartpark.entity.User;
import com.smartpark.security.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/security")
public class SecurityVerificationController {

    private final CurrentUserService currentUserService;

    public SecurityVerificationController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        User user = currentUserService.getCurrentUserEntity();
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @GetMapping("/driver")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> driverOnly() {
        return ResponseEntity.ok(Map.of(
                "access", "GRANTED",
                "message", "Welcome to Driver portal verification endpoint",
                "user", currentUserService.getCurrentUserEmail()
        ));
    }

    @GetMapping("/operator")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<Map<String, String>> operatorOnly() {
        return ResponseEntity.ok(Map.of(
                "access", "GRANTED",
                "message", "Welcome to Operator facility management verification endpoint",
                "user", currentUserService.getCurrentUserEmail()
        ));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminOnly() {
        return ResponseEntity.ok(Map.of(
                "access", "GRANTED",
                "message", "Welcome to Administrator system verification endpoint",
                "user", currentUserService.getCurrentUserEmail()
        ));
    }
}
