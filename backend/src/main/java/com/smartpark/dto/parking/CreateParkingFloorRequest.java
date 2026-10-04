package com.smartpark.dto.parking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateParkingFloorRequest {

    @NotNull(message = "Floor number is required")
    private Integer floorNumber;

    @NotBlank(message = "Floor name is required")
    @Size(max = 100, message = "Floor name must not exceed 100 characters")
    private String name;

    public CreateParkingFloorRequest() {
    }

    public CreateParkingFloorRequest(Integer floorNumber, String name) {
        this.floorNumber = floorNumber;
        this.name = name;
    }

    public Integer getFloorNumber() {
        return floorNumber;
    }

    public void setFloorNumber(Integer floorNumber) {
        this.floorNumber = floorNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
