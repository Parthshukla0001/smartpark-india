package com.smartpark.controller;

import com.smartpark.dto.vehicle.CreateVehicleRequest;
import com.smartpark.dto.vehicle.VehicleResponse;
import com.smartpark.entity.User;
import com.smartpark.entity.Vehicle;
import com.smartpark.exception.ResourceNotFoundException;
import com.smartpark.repository.VehicleRepository;
import com.smartpark.security.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleRepository vehicleRepository;
    private final CurrentUserService currentUserService;

    public VehicleController(VehicleRepository vehicleRepository, CurrentUserService currentUserService) {
        this.vehicleRepository = vehicleRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<List<VehicleResponse>> getMyVehicles() {
        User user = currentUserService.getCurrentUserEntity();
        List<VehicleResponse> vehicles = vehicleRepository.findByUserId(user.getId())
                .stream()
                .filter(Vehicle::isActive)
                .map(VehicleResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(vehicles);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'OPERATOR')")
    @Transactional
    public ResponseEntity<VehicleResponse> registerVehicle(@Valid @RequestBody CreateVehicleRequest request) {
        User user = currentUserService.getCurrentUserEntity();
        String normalizedReg = request.getRegistrationNumber().trim().toUpperCase();

        if (vehicleRepository.existsByRegistrationNumber(normalizedReg)) {
            throw new IllegalArgumentException("Vehicle with registration number '" + normalizedReg + "' already exists");
        }

        Vehicle vehicle = new Vehicle(user, normalizedReg, request.getVehicleType());
        Vehicle saved = vehicleRepository.save(vehicle);
        return ResponseEntity.status(HttpStatus.CREATED).body(VehicleResponse.fromEntity(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'OPERATOR')")
    @Transactional
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id) {
        User user = currentUserService.getCurrentUserEntity();
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        if (!vehicle.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Vehicle not found with id: " + id);
        }

        vehicle.setActive(false);
        vehicleRepository.save(vehicle);
        return ResponseEntity.noContent().build();
    }
}
