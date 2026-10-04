package com.smartpark.controller;

import com.smartpark.dto.common.PageResponse;
import com.smartpark.dto.parking.CreateParkingLotRequest;
import com.smartpark.dto.parking.ParkingLotResponse;
import com.smartpark.dto.parking.UpdateParkingLotRequest;
import com.smartpark.service.ParkingLotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parking-lots")
public class ParkingLotController {

    private final ParkingLotService parkingLotService;

    public ParkingLotController(ParkingLotService parkingLotService) {
        this.parkingLotService = parkingLotService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ParkingLotResponse> createParkingLot(@Valid @RequestBody CreateParkingLotRequest request) {
        ParkingLotResponse response = parkingLotService.createParkingLot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DRIVER')")
    public ResponseEntity<PageResponse<ParkingLotResponse>> getParkingLots(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection
    ) {
        PageResponse<ParkingLotResponse> response = parkingLotService.getParkingLots(
                city, state, pincode, active, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DRIVER')")
    public ResponseEntity<ParkingLotResponse> getParkingLotById(@PathVariable Long id) {
        ParkingLotResponse response = parkingLotService.getParkingLotById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ParkingLotResponse> updateParkingLot(
            @PathVariable Long id,
            @Valid @RequestBody UpdateParkingLotRequest request
    ) {
        ParkingLotResponse response = parkingLotService.updateParkingLot(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateParkingLot(@PathVariable Long id) {
        parkingLotService.deactivateParkingLot(id);
        return ResponseEntity.noContent().build();
    }
}
