package com.smartpark;

import com.smartpark.entity.*;
import com.smartpark.repository.*;
import com.smartpark.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParkingManagementIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingFloorRepository parkingFloorRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    private String adminToken;
    private String operatorToken;
    private String driverToken;

    private ParkingLot testLot;
    private ParkingFloor testFloor;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseThrow();
        Role operatorRole = roleRepository.findByName(RoleType.OPERATOR).orElseThrow();
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();

        User adminUser = userRepository.saveAndFlush(new User("Admin", "User", "admin.pkg@smartpark.in",
                "+919800000001", passwordEncoder.encode("Secret123"), adminRole));
        User operatorUser = userRepository.saveAndFlush(new User("Operator", "User", "operator.pkg@smartpark.in",
                "+919800000002", passwordEncoder.encode("Secret123"), operatorRole));
        User driverUser = userRepository.saveAndFlush(new User("Driver", "User", "driver.pkg@smartpark.in",
                "+919800000003", passwordEncoder.encode("Secret123"), driverRole));

        adminToken = jwtService.generateToken(adminUser.getId(), adminUser.getEmail(), RoleType.ADMIN.name());
        operatorToken = jwtService.generateToken(operatorUser.getId(), operatorUser.getEmail(), RoleType.OPERATOR.name());
        driverToken = jwtService.generateToken(driverUser.getId(), driverUser.getEmail(), RoleType.DRIVER.name());

        testLot = new ParkingLot("Esplanade Smart Hub", "Plot 12, Rasulgarh", "Bhubaneswar", "Odisha",
                "751010", new BigDecimal("20.2961000"), new BigDecimal("85.8245000"));
        testLot.setOpeningTime(LocalTime.of(6, 0));
        testLot.setClosingTime(LocalTime.of(23, 0));
        testLot.setActive(true);
        testLot = parkingLotRepository.saveAndFlush(testLot);

        testFloor = parkingFloorRepository.saveAndFlush(new ParkingFloor(testLot, 1, "Level 1"));
    }

    // =========================================================================
    // 1. PARKING LOT TESTS
    // =========================================================================

    @Test
    @DisplayName("Admin can create a new parking lot")
    void testAdminCanCreateParkingLot() throws Exception {
        String json = """
            {
                "name": "Forum Mall Parking",
                "address": "NH-16, Saheed Nagar",
                "city": "Bhubaneswar",
                "state": "Odisha",
                "pincode": "751007",
                "latitude": 20.2980000,
                "longitude": 85.8350000,
                "openingTime": "08:00:00",
                "closingTime": "22:00:00"
            }
            """;

        mockMvc.perform(post("/api/parking-lots")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Forum Mall Parking")))
                .andExpect(jsonPath("$.city", is("Bhubaneswar")))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("Operator can create a new parking lot")
    void testOperatorCanCreateParkingLot() throws Exception {
        String json = """
            {
                "name": "Infocity Tech Park",
                "address": "Patia",
                "city": "Bhubaneswar",
                "state": "Odisha",
                "pincode": "751024",
                "latitude": 20.3540000,
                "longitude": 85.8180000,
                "openingTime": "06:00:00",
                "closingTime": "23:59:00"
            }
            """;

        mockMvc.perform(post("/api/parking-lots")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Infocity Tech Park")));
    }

    @Test
    @DisplayName("Driver is forbidden from creating parking lot")
    void testDriverCannotCreateParkingLot() throws Exception {
        String json = """
            {
                "name": "Driver Lot",
                "address": "Anywhere",
                "city": "Bhubaneswar",
                "state": "Odisha",
                "pincode": "751001",
                "latitude": 20.3000000,
                "longitude": 85.8000000,
                "openingTime": "08:00:00",
                "closingTime": "20:00:00"
            }
            """;

        mockMvc.perform(post("/api/parking-lots")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("Unauthenticated user cannot access parking lot management")
    void testUnauthenticatedCannotAccess() throws Exception {
        String json = """
            {
                "name": "Ghost Lot",
                "address": "Anywhere",
                "city": "Bhubaneswar",
                "state": "Odisha",
                "pincode": "751001",
                "latitude": 20.3000000,
                "longitude": 85.8000000,
                "openingTime": "08:00:00",
                "closingTime": "20:00:00"
            }
            """;

        mockMvc.perform(post("/api/parking-lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Parking lot creation validation failure on invalid PIN code and closing before opening")
    void testParkingLotValidationFailure() throws Exception {
        // Invalid PIN code (4 digits)
        String badPinJson = """
            {
                "name": "Bad Pin Lot",
                "address": "Address",
                "city": "City",
                "state": "State",
                "pincode": "7510",
                "latitude": 20.3000000,
                "longitude": 85.8000000,
                "openingTime": "08:00:00",
                "closingTime": "20:00:00"
            }
            """;

        mockMvc.perform(post("/api/parking-lots")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPinJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pincode", notNullValue()));

        // Closing time before opening time
        String badTimeJson = """
            {
                "name": "Bad Time Lot",
                "address": "Address",
                "city": "City",
                "state": "State",
                "pincode": "751001",
                "latitude": 20.3000000,
                "longitude": 85.8000000,
                "openingTime": "22:00:00",
                "closingTime": "08:00:00"
            }
            """;

        mockMvc.perform(post("/api/parking-lots")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badTimeJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Closing time must be after opening time")));
    }

    @Test
    @DisplayName("Get parking lot by ID")
    void testGetParkingLotById() throws Exception {
        mockMvc.perform(get("/api/parking-lots/" + testLot.getId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testLot.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Esplanade Smart Hub")));
    }

    @Test
    @DisplayName("Filter parking lots by city and state with pagination")
    void testFilterParkingLots() throws Exception {
        mockMvc.perform(get("/api/parking-lots")
                        .param("city", "Bhubaneswar")
                        .param("state", "Odisha")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "name")
                        .param("sortDirection", "asc")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].city", is("Bhubaneswar")));
    }

    @Test
    @DisplayName("Update parking lot details")
    void testUpdateParkingLot() throws Exception {
        String updateJson = """
            {
                "name": "Esplanade Smart Hub - Renamed",
                "address": "Plot 12, Rasulgarh Ext",
                "city": "Bhubaneswar",
                "state": "Odisha",
                "pincode": "751010",
                "latitude": 20.2961000,
                "longitude": 85.8245000,
                "openingTime": "07:00:00",
                "closingTime": "23:30:00",
                "active": true
            }
            """;

        mockMvc.perform(put("/api/parking-lots/" + testLot.getId())
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Esplanade Smart Hub - Renamed")))
                .andExpect(jsonPath("$.address", is("Plot 12, Rasulgarh Ext")));
    }

    @Test
    @DisplayName("Admin can deactivate parking lot; Driver cannot view deactivated lot")
    void testDeactivateParkingLot() throws Exception {
        mockMvc.perform(delete("/api/parking-lots/" + testLot.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        ParkingLot deactivated = parkingLotRepository.findById(testLot.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();

        // Driver looking up inactive lot receives 404
        mockMvc.perform(get("/api/parking-lots/" + testLot.getId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 2. PARKING FLOOR TESTS
    // =========================================================================

    @Test
    @DisplayName("Create floor under valid lot")
    void testCreateFloor() throws Exception {
        String json = """
            {
                "floorNumber": 2,
                "name": "Level 2"
            }
            """;

        mockMvc.perform(post("/api/parking-lots/" + testLot.getId() + "/floors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.floorNumber", is(2)))
                .andExpect(jsonPath("$.name", is("Level 2")))
                .andExpect(jsonPath("$.parkingLotId", is(testLot.getId().intValue())));
    }

    @Test
    @DisplayName("Reject floor creation under non-existent lot (404)")
    void testCreateFloorNonExistentLot() throws Exception {
        String json = """
            {
                "floorNumber": 1,
                "name": "Ground"
            }
            """;

        mockMvc.perform(post("/api/parking-lots/999999/floors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("Reject floor creation under inactive lot (400 Bad Request)")
    void testCreateFloorUnderInactiveLot() throws Exception {
        testLot.setActive(false);
        parkingLotRepository.saveAndFlush(testLot);

        String json = """
            {
                "floorNumber": 3,
                "name": "Level 3"
            }
            """;

        mockMvc.perform(post("/api/parking-lots/" + testLot.getId() + "/floors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("inactive")));
    }

    @Test
    @DisplayName("Reject duplicate floor number within same parking lot (409 Conflict)")
    void testDuplicateFloorNumberRejected() throws Exception {
        // Floor 1 already exists from setUp
        String json = """
            {
                "floorNumber": 1,
                "name": "Another Level 1"
            }
            """;

        mockMvc.perform(post("/api/parking-lots/" + testLot.getId() + "/floors")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("already exists in parking lot")));
    }

    @Test
    @DisplayName("Driver is forbidden from managing floors")
    void testDriverCannotManageFloors() throws Exception {
        String json = """
            {
                "floorNumber": 2,
                "name": "Level 2"
            }
            """;

        mockMvc.perform(post("/api/parking-lots/" + testLot.getId() + "/floors")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Reject floor request when floor belongs to a different lot (hierarchy mismatch)")
    void testFloorBelongingToDifferentLotRejected() throws Exception {
        ParkingLot otherLot = parkingLotRepository.saveAndFlush(new ParkingLot(
                "Other Lot", "Other Address", "Cuttack", "Odisha",
                "753001", new BigDecimal("20.4625000"), new BigDecimal("85.8830000")
        ));

        // testFloor belongs to testLot, not otherLot
        mockMvc.perform(get("/api/parking-lots/" + otherLot.getId() + "/floors/" + testFloor.getId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("does not belong to parking lot")));
    }

    @Test
    @DisplayName("Cannot delete floor that contains active parking slots")
    void testCannotDeleteFloorWithSlots() throws Exception {
        parkingSlotRepository.saveAndFlush(new ParkingSlot(testFloor, "A-101", SlotType.REGULAR));

        mockMvc.perform(delete("/api/parking-lots/" + testLot.getId() + "/floors/" + testFloor.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("contains 1 parking slot")));
    }

    // =========================================================================
    // 3. PARKING SLOT TESTS
    // =========================================================================

    @Test
    @DisplayName("Create slot under valid floor; defaults status to AVAILABLE")
    void testCreateSlot() throws Exception {
        String json = """
            {
                "slotNumber": "S-101",
                "slotType": "REGULAR"
            }
            """;

        mockMvc.perform(post("/api/parking-lots/" + testLot.getId() + "/floors/" + testFloor.getId() + "/slots")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.slotNumber", is("S-101")))
                .andExpect(jsonPath("$.slotType", is("REGULAR")))
                .andExpect(jsonPath("$.status", is("AVAILABLE")));
    }

    @Test
    @DisplayName("Reject duplicate slot number on same floor (409 Conflict)")
    void testDuplicateSlotNumberRejected() throws Exception {
        parkingSlotRepository.saveAndFlush(new ParkingSlot(testFloor, "S-200", SlotType.EV));

        String json = """
            {
                "slotNumber": "S-200",
                "slotType": "REGULAR"
            }
            """;

        mockMvc.perform(post("/api/parking-lots/" + testLot.getId() + "/floors/" + testFloor.getId() + "/slots")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists on floor")));
    }

    @Test
    @DisplayName("Slot hierarchy validation: rejects slot belonging to different floor")
    void testSlotHierarchyMismatchRejected() throws Exception {
        ParkingFloor otherFloor = parkingFloorRepository.saveAndFlush(new ParkingFloor(testLot, 2, "Level 2"));
        ParkingSlot slotOnOtherFloor = parkingSlotRepository.saveAndFlush(
                new ParkingSlot(otherFloor, "F2-01", SlotType.DISABLED));

        // Request slotOnOtherFloor under testFloor (Level 1)
        mockMvc.perform(get("/api/parking-lots/" + testLot.getId() + "/floors/" + testFloor.getId() + "/slots/" + slotOnOtherFloor.getId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("does not belong to parking floor")));
    }

    @Test
    @DisplayName("Update slot status via PATCH (takes slot OUT_OF_SERVICE)")
    void testUpdateSlotStatus() throws Exception {
        ParkingSlot slot = parkingSlotRepository.saveAndFlush(new ParkingSlot(testFloor, "EV-01", SlotType.EV));

        String json = """
            {
                "status": "OUT_OF_SERVICE"
            }
            """;

        mockMvc.perform(patch("/api/parking-lots/" + testLot.getId() + "/floors/" + testFloor.getId() + "/slots/" + slot.getId() + "/status")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OUT_OF_SERVICE")));
    }

    @Test
    @DisplayName("Deactivate slot via DELETE sets status to OUT_OF_SERVICE")
    void testDeactivateSlotViaDelete() throws Exception {
        ParkingSlot slot = parkingSlotRepository.saveAndFlush(new ParkingSlot(testFloor, "REG-99", SlotType.REGULAR));

        mockMvc.perform(delete("/api/parking-lots/" + testLot.getId() + "/floors/" + testFloor.getId() + "/slots/" + slot.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        ParkingSlot updated = parkingSlotRepository.findById(slot.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SlotStatus.OUT_OF_SERVICE);
    }

    @Test
    @DisplayName("Driver cannot manage slots (403 Forbidden)")
    void testDriverCannotManageSlots() throws Exception {
        String json = """
            {
                "slotNumber": "D-01",
                "slotType": "REGULAR"
            }
            """;

        mockMvc.perform(post("/api/parking-lots/" + testLot.getId() + "/floors/" + testFloor.getId() + "/slots")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }
}
