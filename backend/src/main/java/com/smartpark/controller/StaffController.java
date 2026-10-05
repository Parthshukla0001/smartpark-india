package com.smartpark.controller;

import com.smartpark.dto.user.CreateStaffRequest;
import com.smartpark.dto.user.UserResponse;
import com.smartpark.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllStaff() {
        return ResponseEntity.ok(staffService.getAllStaff());
    }

    @PostMapping
    public ResponseEntity<UserResponse> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        UserResponse response = staffService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateStaffStatus(
            @PathVariable Long id,
            @RequestParam boolean active
    ) {
        UserResponse response = staffService.toggleStaffStatus(id, active);
        return ResponseEntity.ok(response);
    }
}
