package com.smartpark.dto.parking;

import com.smartpark.entity.SlotStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateSlotStatusRequest {

    @NotNull(message = "Slot status is required")
    private SlotStatus status;

    public UpdateSlotStatusRequest() {
    }

    public UpdateSlotStatusRequest(SlotStatus status) {
        this.status = status;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }
}
