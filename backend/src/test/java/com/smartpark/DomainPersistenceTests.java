package com.smartpark;

import com.smartpark.entity.*;
import com.smartpark.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DomainPersistenceTests {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingFloorRepository parkingFloorRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    @DisplayName("1. Flyway migration runs and baseline roles are seeded")
    void testFlywayRolesSeeded() {
        Optional<Role> driverRole = roleRepository.findByName(RoleType.DRIVER);
        Optional<Role> operatorRole = roleRepository.findByName(RoleType.OPERATOR);
        Optional<Role> adminRole = roleRepository.findByName(RoleType.ADMIN);

        assertThat(driverRole).isPresent();
        assertThat(operatorRole).isPresent();
        assertThat(adminRole).isPresent();
    }

    @Test
    @DisplayName("2. User can be persisted and retrieved with Role")
    void testUserPersistence() {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();

        User user = new User("Aarav", "Sharma", "aarav.sharma@example.in", "+919876543210", "hashed_pwd_123", driverRole);
        User savedUser = userRepository.save(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo("aarav.sharma@example.in");
        assertThat(savedUser.getRole().getName()).isEqualTo(RoleType.DRIVER);
        assertThat(savedUser.isActive()).isTrue();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("3. Vehicle can be associated with a User")
    void testVehicleAssociatedWithUser() {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        User user = userRepository.save(new User("Priya", "Patel", "priya.patel@example.in", "+919876543211", "hashed_pwd_456", driverRole));

        Vehicle vehicle = new Vehicle(user, "MH12AB1234", VehicleType.CAR);
        Vehicle savedVehicle = vehicleRepository.save(vehicle);

        assertThat(savedVehicle.getId()).isNotNull();
        assertThat(savedVehicle.getUser().getId()).isEqualTo(user.getId());
        assertThat(savedVehicle.getRegistrationNumber()).isEqualTo("MH12AB1234");
        assertThat(savedVehicle.getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(vehicleRepository.findByUserId(user.getId())).hasSize(1);
    }

    @Test
    @DisplayName("4. ParkingLot -> ParkingFloor -> ParkingSlot hierarchy works")
    void testParkingLotHierarchy() {
        ParkingLot lot = new ParkingLot(
                "Indiranagar Smart Hub",
                "100 Feet Road, HAL 2nd Stage",
                "Bengaluru",
                "Karnataka",
                "560038",
                new BigDecimal("12.9783692"),
                new BigDecimal("77.6408356")
        );
        lot.setOpeningTime(LocalTime.of(6, 0));
        lot.setClosingTime(LocalTime.of(23, 0));
        ParkingLot savedLot = parkingLotRepository.save(lot);
        assertThat(savedLot.getId()).isNotNull();

        ParkingFloor floor = parkingFloorRepository.save(new ParkingFloor(savedLot, 1, "Level 1"));
        assertThat(floor.getId()).isNotNull();
        assertThat(floor.getParkingLot().getId()).isEqualTo(savedLot.getId());

        ParkingSlot slot = parkingSlotRepository.save(new ParkingSlot(floor, "L1-01", SlotType.EV, SlotStatus.AVAILABLE));
        assertThat(slot.getId()).isNotNull();
        assertThat(slot.getParkingFloor().getId()).isEqualTo(floor.getId());
        assertThat(slot.getSlotType()).isEqualTo(SlotType.EV);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);

        assertThat(parkingSlotRepository.findByParkingFloorId(floor.getId())).hasSize(1);
    }

    @Test
    @DisplayName("5. Booking can reference User, Vehicle, and ParkingSlot")
    void testBookingReferencesEntities() {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        User user = userRepository.save(new User("Rohan", "Verma", "rohan.verma@example.in", "+919876543212", "hashed_pwd_789", driverRole));
        Vehicle vehicle = vehicleRepository.save(new Vehicle(user, "DL01AB9999", VehicleType.SUV));

        ParkingLot lot = parkingLotRepository.save(new ParkingLot(
                "Connaught Place Multi-Level",
                "Block C, Inner Circle",
                "New Delhi",
                "Delhi",
                "110001",
                new BigDecimal("28.6315000"),
                new BigDecimal("77.2167000")
        ));
        ParkingFloor floor = parkingFloorRepository.save(new ParkingFloor(lot, 0, "Ground Floor"));
        ParkingSlot slot = parkingSlotRepository.save(new ParkingSlot(floor, "G-10", SlotType.REGULAR, SlotStatus.AVAILABLE));

        Instant startTime = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant endTime = startTime.plus(2, ChronoUnit.HOURS);

        Booking booking = new Booking(user, vehicle, slot, startTime, endTime, BookingStatus.CONFIRMED);
        Booking savedBooking = bookingRepository.save(booking);

        assertThat(savedBooking.getId()).isNotNull();
        assertThat(savedBooking.getUser().getId()).isEqualTo(user.getId());
        assertThat(savedBooking.getVehicle().getId()).isEqualTo(vehicle.getId());
        assertThat(savedBooking.getParkingSlot().getId()).isEqualTo(slot.getId());
        assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(bookingRepository.findByUserId(user.getId())).hasSize(1);
    }

    @Test
    @DisplayName("6. User email unique constraint is enforced by database")
    void testUserEmailUniqueConstraint() {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        userRepository.saveAndFlush(new User("Anil", "Kumar", "unique.test@example.in", "+919876543213", "pwd1", driverRole));

        User duplicateUser = new User("Sunil", "Kumar", "unique.test@example.in", "+919876543214", "pwd2", driverRole);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicateUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("7. Vehicle registration number unique constraint is enforced")
    void testVehicleRegistrationUniqueConstraint() {
        Role driverRole = roleRepository.findByName(RoleType.DRIVER).orElseThrow();
        User user1 = userRepository.save(new User("User1", "Test", "user1@example.in", "+919876543215", "pwd", driverRole));
        User user2 = userRepository.save(new User("User2", "Test", "user2@example.in", "+919876543216", "pwd", driverRole));

        vehicleRepository.saveAndFlush(new Vehicle(user1, "KA01MJ1111", VehicleType.CAR));

        Vehicle duplicateVehicle = new Vehicle(user2, "KA01MJ1111", VehicleType.MOTORCYCLE);

        assertThatThrownBy(() -> vehicleRepository.saveAndFlush(duplicateVehicle))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("8. Parking floor number unique within same lot is enforced")
    void testFloorNumberUniqueWithinLotConstraint() {
        ParkingLot lot = parkingLotRepository.save(new ParkingLot(
                "Pune City Center Parking",
                "FC Road, Shivajinagar",
                "Pune",
                "Maharashtra",
                "411005",
                new BigDecimal("18.5204303"),
                new BigDecimal("73.8567437")
        ));

        parkingFloorRepository.saveAndFlush(new ParkingFloor(lot, 1, "First Floor"));

        ParkingFloor duplicateFloor = new ParkingFloor(lot, 1, "Duplicate Floor 1");

        assertThatThrownBy(() -> parkingFloorRepository.saveAndFlush(duplicateFloor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
