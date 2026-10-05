package com.smartpark.service;

import com.smartpark.dto.parking.CreateParkingFloorRequest;
import com.smartpark.dto.parking.ParkingFloorResponse;
import com.smartpark.dto.parking.UpdateParkingFloorRequest;
import com.smartpark.entity.ParkingFloor;
import com.smartpark.entity.ParkingLot;
import com.smartpark.exception.DuplicateResourceException;
import com.smartpark.exception.ResourceNotFoundException;
import com.smartpark.repository.ParkingFloorRepository;
import com.smartpark.repository.ParkingSlotRepository;
import com.smartpark.security.service.CurrentUserService;
import com.smartpark.security.service.SecurityAuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ParkingFloorService {

    private final ParkingFloorRepository parkingFloorRepository;
    private final ParkingLotService parkingLotService;
    private final ParkingSlotRepository parkingSlotRepository;
    private final CurrentUserService currentUserService;
    private final SecurityAuditLogger auditLogger;
    private final com.smartpark.cache.ParkingCacheService parkingCacheService;

    public ParkingFloorService(ParkingFloorRepository parkingFloorRepository,
                               ParkingLotService parkingLotService,
                               ParkingSlotRepository parkingSlotRepository,
                               CurrentUserService currentUserService,
                               SecurityAuditLogger auditLogger,
                               com.smartpark.cache.ParkingCacheService parkingCacheService) {
        this.parkingFloorRepository = parkingFloorRepository;
        this.parkingLotService = parkingLotService;
        this.parkingSlotRepository = parkingSlotRepository;
        this.currentUserService = currentUserService;
        this.auditLogger = auditLogger;
        this.parkingCacheService = parkingCacheService;
    }

    @Transactional
    public ParkingFloorResponse createFloor(Long lotId, CreateParkingFloorRequest request) {
        ParkingLot lot = parkingLotService.getActiveParkingLotEntity(lotId);

        if (parkingFloorRepository.existsByParkingLotIdAndFloorNumber(lotId, request.getFloorNumber())) {
            throw new DuplicateResourceException("Floor with number " + request.getFloorNumber() + " already exists in parking lot id=" + lotId);
        }

        ParkingFloor floor = new ParkingFloor(lot, request.getFloorNumber(), request.getName().trim());
        ParkingFloor savedFloor = parkingFloorRepository.save(floor);

        String userEmail = currentUserService.getCurrentPrincipal().map(p -> p.getUsername()).orElse("SYSTEM");
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.PARKING_FLOOR_CREATED, userEmail,
                "Created parking floor id=" + savedFloor.getId() + " number=" + savedFloor.getFloorNumber() +
                        " in lot id=" + lotId);

        return ParkingFloorResponse.fromEntity(savedFloor);
    }

    public List<ParkingFloorResponse> getFloorsByLotId(Long lotId) {
        // Validates lot exists (and driver check if inactive)
        parkingLotService.getParkingLotById(lotId);

        List<ParkingFloor> floors = parkingFloorRepository.findByParkingLotIdOrderByFloorNumberAsc(lotId);
        return floors.stream().map(ParkingFloorResponse::fromEntity).toList();
    }

    public ParkingFloorResponse getFloorById(Long lotId, Long floorId) {
        ParkingFloor floor = validateAndGetFloor(lotId, floorId);
        return ParkingFloorResponse.fromEntity(floor);
    }

    @Transactional
    public ParkingFloorResponse updateFloor(Long lotId, Long floorId, UpdateParkingFloorRequest request) {
        // Ensure lot is active before allowing modifications
        parkingLotService.getActiveParkingLotEntity(lotId);
        ParkingFloor floor = validateAndGetFloor(lotId, floorId);

        // Check if updating floor number to another existing floor number
        if (floor.getFloorNumber() != request.getFloorNumber() &&
                parkingFloorRepository.existsByParkingLotIdAndFloorNumber(lotId, request.getFloorNumber())) {
            throw new DuplicateResourceException("Floor with number " + request.getFloorNumber() + " already exists in parking lot id=" + lotId);
        }

        floor.setFloorNumber(request.getFloorNumber());
        floor.setName(request.getName().trim());

        ParkingFloor updatedFloor = parkingFloorRepository.save(floor);

        String userEmail = currentUserService.getCurrentPrincipal().map(p -> p.getUsername()).orElse("SYSTEM");
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.PARKING_FLOOR_UPDATED, userEmail,
                "Updated parking floor id=" + updatedFloor.getId() + " number=" + updatedFloor.getFloorNumber() +
                        " in lot id=" + lotId);

        return ParkingFloorResponse.fromEntity(updatedFloor);
    }

    @Transactional
    public void deleteFloor(Long lotId, Long floorId) {
        parkingLotService.getActiveParkingLotEntity(lotId);
        ParkingFloor floor = validateAndGetFloor(lotId, floorId);

        long slotCount = parkingSlotRepository.countByParkingFloorId(floorId);
        if (slotCount > 0) {
            throw new IllegalStateException("Cannot delete parking floor id=" + floorId + " because it contains " + slotCount +
                    " parking slot(s). Remove or reassign slots first.");
        }

        parkingFloorRepository.delete(floor);

        parkingCacheService.evictFloorSlots(lotId, floorId);

        String userEmail = currentUserService.getCurrentPrincipal().map(p -> p.getUsername()).orElse("SYSTEM");
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.PARKING_FLOOR_DELETED, userEmail,
                "Deleted parking floor id=" + floorId + " from lot id=" + lotId);
    }

    public ParkingFloor validateAndGetFloor(Long lotId, Long floorId) {
        // Ensure lot exists
        parkingLotService.getParkingLotEntity(lotId);

        ParkingFloor floor = parkingFloorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking floor not found with id: " + floorId));

        if (!floor.getParkingLot().getId().equals(lotId)) {
            throw new ResourceNotFoundException("Parking floor id=" + floorId + " does not belong to parking lot id=" + lotId);
        }

        return floor;
    }
}
