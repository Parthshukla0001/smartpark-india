package com.smartpark.dto.vehicle;

import com.smartpark.entity.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class CreateVehicleRequest {

    @NotBlank(message = "Registration number is required")
    @Pattern(regexp = "^[A-Z0-9 -]{4,20}$", message = "Invalid registration number format")
    private String registrationNumber;

    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;

    public CreateVehicleRequest() {
    }

    public CreateVehicleRequest(String registrationNumber, VehicleType vehicleType) {
        this.registrationNumber = registrationNumber;
        this.vehicleType = vehicleType;
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
}
