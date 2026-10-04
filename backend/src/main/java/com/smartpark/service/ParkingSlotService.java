package com.smartpark.service;

import com.smartpark.dto.parking.CreateParkingSlotRequest;
import com.smartpark.dto.parking.ParkingSlotResponse;
import com.smartpark.dto.parking.UpdateParkingSlotRequest;
import com.smartpark.dto.parking.UpdateSlotStatusRequest;
import com.smartpark.entity.ParkingFloor;
import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.SlotStatus;
import com.smartpark.exception.DuplicateResourceException;
import com.smartpark.exception.ResourceNotFoundException;
import com.smartpark.repository.ParkingSlotRepository;
import com.smartpark.security.service.CurrentUserService;
import com.smartpark.security.service.SecurityAuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ParkingSlotService {

    private final ParkingSlotRepository parkingSlotRepository;
    private final ParkingFloorService parkingFloorService;
    private final ParkingLotService parkingLotService;
    private final CurrentUserService currentUserService;
    private final SecurityAuditLogger auditLogger;

    public ParkingSlotService(ParkingSlotRepository parkingSlotRepository,
                              ParkingFloorService parkingFloorService,
                              ParkingLotService parkingLotService,
                              CurrentUserService currentUserService,
                              SecurityAuditLogger auditLogger) {
        this.parkingSlotRepository = parkingSlotRepository;
        this.parkingFloorService = parkingFloorService;
        this.parkingLotService = parkingLotService;
        this.currentUserService = currentUserService;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public ParkingSlotResponse createSlot(Long lotId, Long floorId, CreateParkingSlotRequest request) {
        // Enforce parent lot active
        parkingLotService.getActiveParkingLotEntity(lotId);
        ParkingFloor floor = parkingFloorService.validateAndGetFloor(lotId, floorId);

        String normalizedSlotNumber = request.getSlotNumber().trim();
        if (parkingSlotRepository.existsByParkingFloorIdAndSlotNumber(floorId, normalizedSlotNumber)) {
            throw new DuplicateResourceException("Slot with number '" + normalizedSlotNumber +
                    "' already exists on floor id=" + floorId);
        }

        // Server strictly controls initial status -> defaults to AVAILABLE
        ParkingSlot slot = new ParkingSlot(floor, normalizedSlotNumber, request.getSlotType(), SlotStatus.AVAILABLE);
        ParkingSlot savedSlot = parkingSlotRepository.save(slot);

        String userEmail = currentUserService.getCurrentPrincipal().map(p -> p.getUsername()).orElse("SYSTEM");
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.PARKING_SLOT_CREATED, userEmail,
                "Created slot id=" + savedSlot.getId() + " number='" + savedSlot.getSlotNumber() +
                        "' type=" + savedSlot.getSlotType() + " in lot id=" + lotId + " floor id=" + floorId);

        return ParkingSlotResponse.fromEntity(savedSlot);
    }

    public List<ParkingSlotResponse> getSlotsByFloorId(Long lotId, Long floorId) {
        parkingFloorService.validateAndGetFloor(lotId, floorId);

        List<ParkingSlot> slots = parkingSlotRepository.findByParkingFloorIdOrderBySlotNumberAsc(floorId);
        return slots.stream().map(ParkingSlotResponse::fromEntity).toList();
    }

    public ParkingSlotResponse getSlotById(Long lotId, Long floorId, Long slotId) {
        ParkingSlot slot = validateAndGetSlot(lotId, floorId, slotId);
        return ParkingSlotResponse.fromEntity(slot);
    }

    @Transactional
    public ParkingSlotResponse updateSlot(Long lotId, Long floorId, Long slotId, UpdateParkingSlotRequest request) {
        parkingLotService.getActiveParkingLotEntity(lotId);
        ParkingSlot slot = validateAndGetSlot(lotId, floorId, slotId);

        String normalizedSlotNumber = request.getSlotNumber().trim();
        if (!slot.getSlotNumber().equalsIgnoreCase(normalizedSlotNumber) &&
                parkingSlotRepository.existsByParkingFloorIdAndSlotNumber(floorId, normalizedSlotNumber)) {
            throw new DuplicateResourceException("Slot with number '" + normalizedSlotNumber +
                    "' already exists on floor id=" + floorId);
        }

        slot.setSlotNumber(normalizedSlotNumber);
        slot.setSlotType(request.getSlotType());
        // Note: operational status is preserved and not overwritten by metadata update

        ParkingSlot updatedSlot = parkingSlotRepository.save(slot);

        String userEmail = currentUserService.getCurrentPrincipal().map(p -> p.getUsername()).orElse("SYSTEM");
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.PARKING_SLOT_UPDATED, userEmail,
                "Updated slot id=" + updatedSlot.getId() + " number='" + updatedSlot.getSlotNumber() +
                        "' type=" + updatedSlot.getSlotType());

        return ParkingSlotResponse.fromEntity(updatedSlot);
    }

    @Transactional
    public ParkingSlotResponse updateSlotStatus(Long lotId, Long floorId, Long slotId, UpdateSlotStatusRequest request) {
        parkingLotService.getActiveParkingLotEntity(lotId);
        ParkingSlot slot = validateAndGetSlot(lotId, floorId, slotId);

        slot.setStatus(request.getStatus());
        ParkingSlot updatedSlot = parkingSlotRepository.save(slot);

        String userEmail = currentUserService.getCurrentPrincipal().map(p -> p.getUsername()).orElse("SYSTEM");
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.PARKING_SLOT_STATUS_CHANGED, userEmail,
                "Updated slot status id=" + updatedSlot.getId() + " to " + updatedSlot.getStatus());

        return ParkingSlotResponse.fromEntity(updatedSlot);
    }

    @Transactional
    public void deactivateSlot(Long lotId, Long floorId, Long slotId) {
        parkingLotService.getActiveParkingLotEntity(lotId);
        ParkingSlot slot = validateAndGetSlot(lotId, floorId, slotId);

        // Safe soft-deactivation to preserve historical booking references
        slot.setStatus(SlotStatus.OUT_OF_SERVICE);
        parkingSlotRepository.save(slot);

        String userEmail = currentUserService.getCurrentPrincipal().map(p -> p.getUsername()).orElse("SYSTEM");
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.PARKING_SLOT_DEACTIVATED, userEmail,
                "Deactivated slot id=" + slot.getId() + " (status OUT_OF_SERVICE)");
    }

    public ParkingSlot validateAndGetSlot(Long lotId, Long floorId, Long slotId) {
        ParkingFloor floor = parkingFloorService.validateAndGetFloor(lotId, floorId);

        ParkingSlot slot = parkingSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking slot not found with id: " + slotId));

        if (!slot.getParkingFloor().getId().equals(floorId)) {
            throw new ResourceNotFoundException("Parking slot id=" + slotId +
                    " does not belong to parking floor id=" + floorId);
        }

        return slot;
    }
}
