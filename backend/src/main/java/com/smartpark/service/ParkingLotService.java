package com.smartpark.service;

import com.smartpark.cache.ParkingCacheService;
import com.smartpark.dto.common.PageResponse;
import com.smartpark.dto.parking.CreateParkingLotRequest;
import com.smartpark.dto.parking.ParkingLotResponse;
import com.smartpark.dto.parking.UpdateParkingLotRequest;
import com.smartpark.entity.ParkingLot;
import com.smartpark.exception.ResourceNotFoundException;
import com.smartpark.repository.ParkingLotRepository;
import com.smartpark.security.service.CurrentUserService;
import com.smartpark.security.service.SecurityAuditLogger;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ParkingLotService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "city", "state", "pincode", "createdAt", "updatedAt"
    );
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ParkingLotRepository parkingLotRepository;
    private final CurrentUserService currentUserService;
    private final SecurityAuditLogger auditLogger;
    private final ParkingCacheService parkingCacheService;

    public ParkingLotService(ParkingLotRepository parkingLotRepository,
                              CurrentUserService currentUserService,
                              SecurityAuditLogger auditLogger,
                              ParkingCacheService parkingCacheService) {
        this.parkingLotRepository = parkingLotRepository;
        this.currentUserService = currentUserService;
        this.auditLogger = auditLogger;
        this.parkingCacheService = parkingCacheService;
    }


    @Transactional
    public ParkingLotResponse createParkingLot(CreateParkingLotRequest request) {
        validateOperatingHours(request.getOpeningTime(), request.getClosingTime());

        ParkingLot parkingLot = new ParkingLot(
                request.getName().trim(),
                request.getAddress().trim(),
                request.getCity().trim(),
                request.getState().trim(),
                request.getPincode().trim(),
                request.getLatitude(),
                request.getLongitude()
        );
        parkingLot.setOpeningTime(request.getOpeningTime());
        parkingLot.setClosingTime(request.getClosingTime());
        parkingLot.setActive(true);

        ParkingLot savedLot = parkingLotRepository.save(parkingLot);

        String userEmail = currentUserService.getCurrentPrincipal().map(p -> p.getUsername()).orElse("SYSTEM");
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.PARKING_LOT_CREATED, userEmail,
                "Created parking lot id=" + savedLot.getId() + " name='" + savedLot.getName() + "' in " + savedLot.getCity());

        parkingCacheService.evictAllParkingLots();

        return ParkingLotResponse.fromEntity(savedLot);
    }

    public PageResponse<ParkingLotResponse> getParkingLots(String city, String state, String pincode, Boolean active,
                                                          int page, int size, String sortBy, String sortDirection) {
        // Enforce safe pagination boundaries
        if (page < 0) {
            page = 0;
        }
        if (size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        } else if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }

        // Whitelist sort field
        String sortField = (sortBy != null && !sortBy.isBlank()) ? sortBy.trim() : "name";
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new IllegalArgumentException("Invalid sort field: '" + sortField + "'. Allowed sort fields: " + ALLOWED_SORT_FIELDS);
        }

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        // Drivers may only view active parking lots
        boolean isDriver = currentUserService.getCurrentPrincipal()
                .map(p -> p.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER")))
                .orElse(false);

        Boolean effectiveActive = isDriver ? Boolean.TRUE : active;

        // Check cache-aside before hitting PostgreSQL
        String cacheKey = "city=" + (city != null ? city.trim().toLowerCase() : "") +
                ":state=" + (state != null ? state.trim().toLowerCase() : "") +
                ":pincode=" + (pincode != null ? pincode.trim() : "") +
                ":active=" + effectiveActive +
                ":page=" + page + ":size=" + size +
                ":sort=" + sortField + ":" + direction;

        Optional<PageResponse<ParkingLotResponse>> cachedPage = parkingCacheService.getParkingLotsPage(cacheKey);
        if (cachedPage.isPresent()) {
            return cachedPage.get();
        }

        Specification<ParkingLot> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (city != null && !city.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("city")), city.trim().toLowerCase()));
            }
            if (state != null && !state.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("state")), state.trim().toLowerCase()));
            }
            if (pincode != null && !pincode.isBlank()) {
                predicates.add(cb.equal(root.get("pincode"), pincode.trim()));
            }
            if (effectiveActive != null) {
                predicates.add(cb.equal(root.get("active"), effectiveActive));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ParkingLot> parkingLotPage = parkingLotRepository.findAll(spec, pageable);
        Page<ParkingLotResponse> mappedPage = parkingLotPage.map(ParkingLotResponse::fromEntity);
        PageResponse<ParkingLotResponse> responsePage = PageResponse.from(mappedPage);

        // Populate cache for subsequent reads
        parkingCacheService.putParkingLotsPage(cacheKey, responsePage);

        return responsePage;
    }

    public ParkingLotResponse getParkingLotById(Long id) {
        boolean isDriver = currentUserService.getCurrentPrincipal()
                .map(p -> p.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER")))
                .orElse(false);

        // Check cache-aside before hitting PostgreSQL
        Optional<ParkingLotResponse> cached = parkingCacheService.getParkingLot(id);
        if (cached.isPresent()) {
            ParkingLotResponse lot = cached.get();
            if (isDriver && !lot.isActive()) {
                throw new ResourceNotFoundException("Parking lot not found with id: " + id);
            }
            return lot;
        }

        ParkingLot lot = parkingLotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found with id: " + id));

        if (isDriver && !lot.isActive()) {
            throw new ResourceNotFoundException("Parking lot not found with id: " + id);
        }

        ParkingLotResponse response = ParkingLotResponse.fromEntity(lot);
        parkingCacheService.putParkingLot(id, response);

        return response;
    }

    @Transactional
    public ParkingLotResponse updateParkingLot(Long id, UpdateParkingLotRequest request) {
        validateOperatingHours(request.getOpeningTime(), request.getClosingTime());

        ParkingLot lot = parkingLotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found with id: " + id));

        lot.setName(request.getName().trim());
        lot.setAddress(request.getAddress().trim());
        lot.setCity(request.getCity().trim());
        lot.setState(request.getState().trim());
        lot.setPincode(request.getPincode().trim());
        lot.setLatitude(request.getLatitude());
        lot.setLongitude(request.getLongitude());
        lot.setOpeningTime(request.getOpeningTime());
        lot.setClosingTime(request.getClosingTime());

        if (request.getActive() != null) {
            lot.setActive(request.getActive());
        }

        ParkingLot updatedLot = parkingLotRepository.save(lot);

        String userEmail = currentUserService.getCurrentPrincipal().map(p -> p.getUsername()).orElse("SYSTEM");
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.PARKING_LOT_UPDATED, userEmail,
                "Updated parking lot id=" + updatedLot.getId() + " name='" + updatedLot.getName() + "' active=" + updatedLot.isActive());

        // Invalidate affected cache entries after commit
        parkingCacheService.evictParkingLot(id);

        return ParkingLotResponse.fromEntity(updatedLot);
    }

    @Transactional
    public void deactivateParkingLot(Long id) {
        ParkingLot lot = parkingLotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found with id: " + id));

        lot.setActive(false);
        parkingLotRepository.save(lot);

        String userEmail = currentUserService.getCurrentPrincipal().map(p -> p.getUsername()).orElse("SYSTEM");
        auditLogger.logEvent(SecurityAuditLogger.SecurityEvent.PARKING_LOT_DEACTIVATED, userEmail,
                "Deactivated parking lot id=" + lot.getId() + " name='" + lot.getName() + "'");

        // Invalidate affected cache entries after commit
        parkingCacheService.evictParkingLot(id);
    }


    public ParkingLot getActiveParkingLotEntity(Long id) {
        ParkingLot lot = parkingLotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found with id: " + id));
        if (!lot.isActive()) {
            throw new IllegalStateException("Parking lot id=" + id + " is inactive. Cannot create or modify resources under an inactive parking lot.");
        }
        return lot;
    }

    public ParkingLot getParkingLotEntity(Long id) {
        return parkingLotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found with id: " + id));
    }

    private void validateOperatingHours(java.time.LocalTime openingTime, java.time.LocalTime closingTime) {
        if (openingTime != null && closingTime != null) {
            if (closingTime.equals(openingTime)) {
                throw new IllegalArgumentException("Closing time cannot be equal to opening time");
            }
            if (closingTime.isBefore(openingTime)) {
                throw new IllegalArgumentException("Closing time must be after opening time");
            }
        }
    }
}
