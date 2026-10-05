package com.smartpark.dto.vehicle;

import com.smartpark.entity.Vehicle;
import com.smartpark.entity.VehicleType;

import java.time.Instant;

public class VehicleResponse {

    private Long id;
    private Long userId;
    private String registrationNumber;
    private VehicleType vehicleType;
    private boolean active;
    private Instant createdAt;

    public VehicleResponse() {
    }

    public VehicleResponse(Long id, Long userId, String registrationNumber, VehicleType vehicleType, boolean active, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.registrationNumber = registrationNumber;
        this.vehicleType = vehicleType;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static VehicleResponse fromEntity(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getUser() != null ? vehicle.getUser().getId() : null,
                vehicle.getRegistrationNumber(),
                vehicle.getVehicleType(),
                vehicle.isActive(),
                vehicle.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
