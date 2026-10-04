package com.smartpark.dto.parking;

import com.smartpark.entity.SlotType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateParkingSlotRequest {

    @NotBlank(message = "Slot number is required")
    @Size(max = 50, message = "Slot number must not exceed 50 characters")
    private String slotNumber;

    @NotNull(message = "Slot type is required")
    private SlotType slotType;

    public UpdateParkingSlotRequest() {
    }

    public UpdateParkingSlotRequest(String slotNumber, SlotType slotType) {
        this.slotNumber = slotNumber;
        this.slotType = slotType;
    }

    public String getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(String slotNumber) {
        this.slotNumber = slotNumber;
    }

    public SlotType getSlotType() {
        return slotType;
    }

    public void setSlotType(SlotType slotType) {
        this.slotType = slotType;
    }
}
