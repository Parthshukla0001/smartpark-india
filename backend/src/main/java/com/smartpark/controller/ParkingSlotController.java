package com.smartpark.controller;

import com.smartpark.dto.parking.CreateParkingSlotRequest;
import com.smartpark.dto.parking.ParkingSlotResponse;
import com.smartpark.dto.parking.UpdateParkingSlotRequest;
import com.smartpark.dto.parking.UpdateSlotStatusRequest;
import com.smartpark.service.ParkingSlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parking-lots/{lotId}/floors/{floorId}/slots")
public class ParkingSlotController {

    private final ParkingSlotService parkingSlotService;

    public ParkingSlotController(ParkingSlotService parkingSlotService) {
        this.parkingSlotService = parkingSlotService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ParkingSlotResponse> createSlot(
            @PathVariable Long lotId,
            @PathVariable Long floorId,
            @Valid @RequestBody CreateParkingSlotRequest request
    ) {
        ParkingSlotResponse response = parkingSlotService.createSlot(lotId, floorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DRIVER')")
    public ResponseEntity<List<ParkingSlotResponse>> getSlots(
            @PathVariable Long lotId,
            @PathVariable Long floorId
    ) {
        List<ParkingSlotResponse> response = parkingSlotService.getSlotsByFloorId(lotId, floorId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DRIVER')")
    public ResponseEntity<ParkingSlotResponse> getSlotById(
            @PathVariable Long lotId,
            @PathVariable Long floorId,
            @PathVariable Long slotId
    ) {
        ParkingSlotResponse response = parkingSlotService.getSlotById(lotId, floorId, slotId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ParkingSlotResponse> updateSlot(
            @PathVariable Long lotId,
            @PathVariable Long floorId,
            @PathVariable Long slotId,
            @Valid @RequestBody UpdateParkingSlotRequest request
    ) {
        ParkingSlotResponse response = parkingSlotService.updateSlot(lotId, floorId, slotId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{slotId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ParkingSlotResponse> updateSlotStatus(
            @PathVariable Long lotId,
            @PathVariable Long floorId,
            @PathVariable Long slotId,
            @Valid @RequestBody UpdateSlotStatusRequest request
    ) {
        ParkingSlotResponse response = parkingSlotService.updateSlotStatus(lotId, floorId, slotId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Void> deactivateSlot(
            @PathVariable Long lotId,
            @PathVariable Long floorId,
            @PathVariable Long slotId
    ) {
        parkingSlotService.deactivateSlot(lotId, floorId, slotId);
        return ResponseEntity.noContent().build();
    }
}
