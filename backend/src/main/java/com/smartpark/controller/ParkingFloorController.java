package com.smartpark.controller;

import com.smartpark.dto.parking.CreateParkingFloorRequest;
import com.smartpark.dto.parking.ParkingFloorResponse;
import com.smartpark.dto.parking.UpdateParkingFloorRequest;
import com.smartpark.service.ParkingFloorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parking-lots/{lotId}/floors")
public class ParkingFloorController {

    private final ParkingFloorService parkingFloorService;

    public ParkingFloorController(ParkingFloorService parkingFloorService) {
        this.parkingFloorService = parkingFloorService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ParkingFloorResponse> createFloor(
            @PathVariable Long lotId,
            @Valid @RequestBody CreateParkingFloorRequest request
    ) {
        ParkingFloorResponse response = parkingFloorService.createFloor(lotId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DRIVER')")
    public ResponseEntity<List<ParkingFloorResponse>> getFloors(@PathVariable Long lotId) {
        List<ParkingFloorResponse> response = parkingFloorService.getFloorsByLotId(lotId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{floorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DRIVER')")
    public ResponseEntity<ParkingFloorResponse> getFloorById(
            @PathVariable Long lotId,
            @PathVariable Long floorId
    ) {
        ParkingFloorResponse response = parkingFloorService.getFloorById(lotId, floorId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{floorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ParkingFloorResponse> updateFloor(
            @PathVariable Long lotId,
            @PathVariable Long floorId,
            @Valid @RequestBody UpdateParkingFloorRequest request
    ) {
        ParkingFloorResponse response = parkingFloorService.updateFloor(lotId, floorId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{floorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteFloor(
            @PathVariable Long lotId,
            @PathVariable Long floorId
    ) {
        parkingFloorService.deleteFloor(lotId, floorId);
        return ResponseEntity.noContent().build();
    }
}
