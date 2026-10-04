package com.smartpark.dto.parking;

import com.smartpark.entity.ParkingLot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

public class ParkingLotResponse {

    private Long id;
    private String name;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public ParkingLotResponse() {
    }

    public ParkingLotResponse(Long id, String name, String address, String city, String state, String pincode,
                              BigDecimal latitude, BigDecimal longitude, LocalTime openingTime,
                              LocalTime closingTime, boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ParkingLotResponse fromEntity(ParkingLot lot) {
        if (lot == null) return null;
        return new ParkingLotResponse(
                lot.getId(),
                lot.getName(),
                lot.getAddress(),
                lot.getCity(),
                lot.getState(),
                lot.getPincode(),
                lot.getLatitude(),
                lot.getLongitude(),
                lot.getOpeningTime(),
                lot.getClosingTime(),
                lot.isActive(),
                lot.getCreatedAt(),
                lot.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public void setOpeningTime(LocalTime openingTime) {
        this.openingTime = openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public void setClosingTime(LocalTime closingTime) {
        this.closingTime = closingTime;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
