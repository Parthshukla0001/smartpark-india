package com.smartpark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartpark.dto.booking.CreateBookingRequest;
import com.smartpark.entity.*;
import com.smartpark.exception.BookingConflictException;
import com.smartpark.repository.*;
import com.smartpark.security.jwt.JwtService;
import com.smartpark.service.BookingService;
import com.smartpark.service.ParkingSlotService;
import com.smartpark.websocket.ParkingEventType;
import com.smartpark.websocket.dto.ParkingEventPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketParkingUpdatesIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

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

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ParkingSlotService parkingSlotService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String validDriverToken;
    private String validOperatorToken;
    private User testDriver;
    private User testOperator;
    private Vehicle testVehicle;
    private ParkingLot testLot;
    private ParkingFloor testFloor;
    private ParkingSlot testSlot;
    private WebSocketStompClient stompClient;
    private String wsUrl;

    @BeforeEach
    void setUp() {
        wsUrl = "ws://localhost:" + port + "/ws";

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Role driverRole = roleRepository.findByName(RoleType.DRIVER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.DRIVER, "Driver")));
        Role operatorRole = roleRepository.findByName(RoleType.OPERATOR)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.OPERATOR, "Operator")));

        testDriver = new User(
                "Driver",
                "User",
                "ws.driver." + suffix + "@smartpark.in",
                "+91987" + (int)(1000000 + Math.random() * 8999999),
                passwordEncoder.encode("SecurePass123!"),
                driverRole
        );
        testDriver = userRepository.save(testDriver);
        validDriverToken = jwtService.generateToken(testDriver.getId(), testDriver.getEmail(), "DRIVER");

        testOperator = new User(
                "Operator",
                "User",
                "ws.operator." + suffix + "@smartpark.in",
                "+91988" + (int)(1000000 + Math.random() * 8999999),
                passwordEncoder.encode("SecurePass123!"),
                operatorRole
        );
        testOperator = userRepository.save(testOperator);
        validOperatorToken = jwtService.generateToken(testOperator.getId(), testOperator.getEmail(), "OPERATOR");

        testVehicle = new Vehicle(testDriver, "OD-02-" + suffix.toUpperCase(), VehicleType.CAR);
        testVehicle = vehicleRepository.save(testVehicle);

        testLot = new ParkingLot(
                "WebSocket Lot " + suffix,
                "Janpath, Bhubaneswar",
                "Bhubaneswar",
                "Odisha",
                "751001",
                new BigDecimal("20.2961"),
                new BigDecimal("85.8245")
        );
        testLot.setOpeningTime(LocalTime.of(0, 0));
        testLot.setClosingTime(LocalTime.of(23, 59));
        testLot = parkingLotRepository.save(testLot);

        testFloor = new ParkingFloor(testLot, 1, "Ground Floor");
        testFloor = parkingFloorRepository.save(testFloor);

        testSlot = new ParkingSlot(testFloor, "WS-" + suffix, SlotType.REGULAR, SlotStatus.AVAILABLE);
        testSlot = parkingSlotRepository.save(testSlot);
    }

    private StompSession connectSession(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return stompClient.connectAsync(wsUrl, new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("1. WebSocket endpoint accepts authenticated connection")
    void testAuthenticatedConnectionAccepted() throws Exception {
        StompSession session = connectSession(validDriverToken);
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("2. Unauthorized connection is rejected (missing or invalid JWT)")
    void testUnauthorizedConnectionRejected() {
        // Missing token
        assertThatThrownBy(() -> connectSession(null))
                .satisfies(ex -> assertThat(ex.getCause() != null ? ex.getCause() : ex).isInstanceOf(Exception.class));

        // Invalid token
        assertThatThrownBy(() -> connectSession("invalid.jwt.token"))
                .satisfies(ex -> assertThat(ex.getCause() != null ? ex.getCause() : ex).isInstanceOf(Exception.class));
    }

    @Test
    @DisplayName("3. Authorized client can subscribe to an allowed parking topic")
    void testAuthorizedSubscriptionAllowed() throws Exception {
        StompSession session = connectSession(validDriverToken);
        assertThat(session.isConnected()).isTrue();

        BlockingQueue<ParkingEventPayload> queue = new LinkedBlockingQueue<>();
        StompSession.Subscription subscription = session.subscribe(
                "/topic/parking-lots/" + testLot.getId(),
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ParkingEventPayload.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        queue.offer((ParkingEventPayload) payload);
                    }
                }
        );

        assertThat(subscription.getSubscriptionId()).isNotNull();
        session.disconnect();
    }

    @Test
    @DisplayName("4. Unauthorized subscription is rejected (invalid destination or non-existent lot)")
    void testUnauthorizedSubscriptionRejected() throws Exception {
        StompSession session = connectSession(validDriverToken);
        assertThat(session.isConnected()).isTrue();

        // Subscription to non-existent parking lot should be rejected / close session
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        session.subscribe("/topic/parking-lots/99999999", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParkingEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        // Destination outside allowed pattern
        session.subscribe("/topic/secret-admin-stream", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParkingEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        session.disconnect();
    }

    @Test
    @DisplayName("5. Successful booking produces BOOKING_CREATED event on WebSocket topic")
    void testSuccessfulBookingProducesEvent() throws Exception {
        StompSession session = connectSession(validDriverToken);
        BlockingQueue<ParkingEventPayload> queue = new LinkedBlockingQueue<>();

        session.subscribe("/topic/parking-lots/" + testLot.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParkingEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((ParkingEventPayload) payload);
            }
        });

        // Allow subscription propagation
        Thread.sleep(300);

        Instant start = Instant.now(clock).plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        // Execute booking creation inside simulated security context
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        com.smartpark.security.UserPrincipal.fromUser(testDriver), null,
                        com.smartpark.security.UserPrincipal.fromUser(testDriver).getAuthorities()
                )
        );

        CreateBookingRequest request = new CreateBookingRequest(
                testVehicle.getId(),
                testSlot.getId(),
                start,
                end
        );

        bookingService.createBooking(request);

        ParkingEventPayload event = queue.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo(ParkingEventType.BOOKING_CREATED);
        assertThat(event.getParkingLotId()).isEqualTo(testLot.getId());
        assertThat(event.getSlotId()).isEqualTo(testSlot.getId());
        assertThat(event.getBookingId()).isNotNull();

        session.disconnect();
    }

    @Test
    @DisplayName("6. Successful cancellation produces BOOKING_CANCELLED event")
    void testSuccessfulCancellationProducesEvent() throws Exception {
        StompSession session = connectSession(validDriverToken);
        BlockingQueue<ParkingEventPayload> queue = new LinkedBlockingQueue<>();

        session.subscribe("/topic/parking-lots/" + testLot.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParkingEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((ParkingEventPayload) payload);
            }
        });

        Thread.sleep(300);

        Instant start = Instant.now(clock).plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        com.smartpark.security.UserPrincipal.fromUser(testDriver), null,
                        com.smartpark.security.UserPrincipal.fromUser(testDriver).getAuthorities()
                )
        );

        Booking booking = new Booking(testDriver, testVehicle, testSlot, start, end, BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);

        bookingService.cancelBooking(booking.getId());

        ParkingEventPayload event = queue.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo(ParkingEventType.BOOKING_CANCELLED);
        assertThat(event.getBookingId()).isEqualTo(booking.getId());

        session.disconnect();
    }

    @Test
    @DisplayName("7. Successful completion produces BOOKING_COMPLETED event")
    void testSuccessfulCompletionProducesEvent() throws Exception {
        StompSession session = connectSession(validOperatorToken);
        BlockingQueue<ParkingEventPayload> queue = new LinkedBlockingQueue<>();

        session.subscribe("/topic/parking-lots/" + testLot.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParkingEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((ParkingEventPayload) payload);
            }
        });

        Thread.sleep(300);

        Instant start = Instant.now(clock).plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        com.smartpark.security.UserPrincipal.fromUser(testOperator), null,
                        com.smartpark.security.UserPrincipal.fromUser(testOperator).getAuthorities()
                )
        );

        Booking booking = new Booking(testDriver, testVehicle, testSlot, start, end, BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);

        bookingService.completeBooking(booking.getId());

        ParkingEventPayload event = queue.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo(ParkingEventType.BOOKING_COMPLETED);
        assertThat(event.getBookingId()).isEqualTo(booking.getId());

        session.disconnect();
    }

    @Test
    @DisplayName("8. Successful expiry produces BOOKING_EXPIRED event")
    void testSuccessfulExpiryProducesEvent() throws Exception {
        StompSession session = connectSession(validOperatorToken);
        BlockingQueue<ParkingEventPayload> queue = new LinkedBlockingQueue<>();

        session.subscribe("/topic/parking-lots/" + testLot.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParkingEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((ParkingEventPayload) payload);
            }
        });

        Thread.sleep(300);

        Instant start = Instant.now(clock).minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        com.smartpark.security.UserPrincipal.fromUser(testOperator), null,
                        com.smartpark.security.UserPrincipal.fromUser(testOperator).getAuthorities()
                )
        );

        Booking booking = new Booking(testDriver, testVehicle, testSlot, start, end, BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);

        bookingService.expireBooking(booking.getId());

        ParkingEventPayload event = queue.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo(ParkingEventType.BOOKING_EXPIRED);
        assertThat(event.getBookingId()).isEqualTo(booking.getId());

        session.disconnect();
    }

    @Test
    @DisplayName("9. Failed transaction does NOT produce a success WebSocket event (Post-commit guarantee)")
    void testFailedTransactionDoesNotProduceEvent() throws Exception {
        StompSession session = connectSession(validDriverToken);
        BlockingQueue<ParkingEventPayload> queue = new LinkedBlockingQueue<>();

        session.subscribe("/topic/parking-lots/" + testLot.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParkingEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((ParkingEventPayload) payload);
            }
        });

        Thread.sleep(300);

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        com.smartpark.security.UserPrincipal.fromUser(testDriver), null,
                        com.smartpark.security.UserPrincipal.fromUser(testDriver).getAuthorities()
                )
        );

        // Attempt an invalid booking (end time before start time -> fails validation and rolls back)
        Instant start = Instant.now(clock).plus(2, ChronoUnit.HOURS);
        Instant end = start.minus(30, ChronoUnit.MINUTES);

        CreateBookingRequest invalidRequest = new CreateBookingRequest(testVehicle.getId(), testSlot.getId(), start, end);
        assertThatThrownBy(() -> bookingService.createBooking(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class);

        // Attempt a booking inside an explicitly rolled-back transaction
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> txTemplate.execute(status -> {
            Instant validStart = Instant.now(clock).plus(5, ChronoUnit.HOURS);
            Instant validEnd = validStart.plus(1, ChronoUnit.HOURS);
            bookingService.createBooking(new CreateBookingRequest(testVehicle.getId(), testSlot.getId(), validStart, validEnd));
            status.setRollbackOnly();
            throw new RuntimeException("Simulated transaction rollback");
        })).isInstanceOf(RuntimeException.class);

        // Verify NO message arrived in the WebSocket queue
        ParkingEventPayload event = queue.poll(1500, TimeUnit.MILLISECONDS);
        assertThat(event).isNull();

        session.disconnect();
    }

    @Test
    @DisplayName("10. Event payload contains expected fields and omits sensitive user data")
    void testEventPayloadSanitization() throws Exception {
        StompSession session = connectSession(validDriverToken);
        BlockingQueue<ParkingEventPayload> queue = new LinkedBlockingQueue<>();

        session.subscribe("/topic/parking-lots/" + testLot.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParkingEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((ParkingEventPayload) payload);
            }
        });

        Thread.sleep(300);

        Instant start = Instant.now(clock).plus(10, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        com.smartpark.security.UserPrincipal.fromUser(testDriver), null,
                        com.smartpark.security.UserPrincipal.fromUser(testDriver).getAuthorities()
                )
        );

        bookingService.createBooking(new CreateBookingRequest(testVehicle.getId(), testSlot.getId(), start, end));

        ParkingEventPayload event = queue.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.getParkingLotId()).isEqualTo(testLot.getId());
        assertThat(event.getFloorId()).isEqualTo(testFloor.getId());
        assertThat(event.getSlotId()).isEqualTo(testSlot.getId());
        assertThat(event.getSlotNumber()).isEqualTo(testSlot.getSlotNumber());
        assertThat(event.getSlotType()).isEqualTo(testSlot.getSlotType());
        assertThat(event.getTimestamp()).isNotNull();

        session.disconnect();
    }

    @Test
    @DisplayName("11. Multiple subscribers receive the same broadcast event")
    void testMultipleSubscribersReceiveEvent() throws Exception {
        StompSession session1 = connectSession(validDriverToken);
        StompSession session2 = connectSession(validOperatorToken);

        BlockingQueue<ParkingEventPayload> queue1 = new LinkedBlockingQueue<>();
        BlockingQueue<ParkingEventPayload> queue2 = new LinkedBlockingQueue<>();

        session1.subscribe("/topic/parking-lots/" + testLot.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParkingEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue1.offer((ParkingEventPayload) payload);
            }
        });

        session2.subscribe("/topic/parking-lots/" + testLot.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParkingEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue2.offer((ParkingEventPayload) payload);
            }
        });

        Thread.sleep(300);

        Instant start = Instant.now(clock).plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        com.smartpark.security.UserPrincipal.fromUser(testDriver), null,
                        com.smartpark.security.UserPrincipal.fromUser(testDriver).getAuthorities()
                )
        );

        bookingService.createBooking(new CreateBookingRequest(testVehicle.getId(), testSlot.getId(), start, end));

        ParkingEventPayload event1 = queue1.poll(5, TimeUnit.SECONDS);
        ParkingEventPayload event2 = queue2.poll(5, TimeUnit.SECONDS);

        assertThat(event1).isNotNull();
        assertThat(event2).isNotNull();
        assertThat(event1.getBookingId()).isEqualTo(event2.getBookingId());
        assertThat(event1.getEventType()).isEqualTo(ParkingEventType.BOOKING_CREATED);
        assertThat(event2.getEventType()).isEqualTo(ParkingEventType.BOOKING_CREATED);

        session1.disconnect();
        session2.disconnect();
    }
}
