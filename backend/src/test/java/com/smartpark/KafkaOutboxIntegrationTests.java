package com.smartpark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.dto.booking.CreateBookingRequest;
import com.smartpark.entity.*;
import com.smartpark.kafka.consumer.NotificationConsumer;
import com.smartpark.kafka.event.BookingKafkaEvent;
import com.smartpark.kafka.outbox.OutboxPublisher;
import com.smartpark.kafka.outbox.OutboxService;
import com.smartpark.repository.*;
import com.smartpark.service.BookingService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
public class KafkaOutboxIntegrationTests {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private NotificationConsumer notificationConsumer;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${smartpark.kafka.topics.booking-events:smartpark.booking.events}")
    private String bookingEventsTopic;

    @Value("${smartpark.kafka.topics.booking-events-dlt:smartpark.booking.events.DLT}")
    private String bookingEventsDltTopic;

    private User testDriver;
    private Vehicle testVehicle;
    private ParkingLot testLot;
    private ParkingFloor testFloor;
    private ParkingSlot testSlot;
    private final ZoneId istZone = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        cleanDatabase();
        outboxPublisher.setOutboxEnabled(false);

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Role driverRole = roleRepository.findByName(RoleType.DRIVER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.DRIVER, "Driver")));

        testDriver = new User(
                "Kafka",
                "Tester",
                "kafka.test." + suffix + "@smartpark.in",
                "+91987" + (int) (1000000 + Math.random() * 8999999),
                passwordEncoder.encode("Password123!"),
                driverRole
        );
        testDriver = userRepository.saveAndFlush(testDriver);

        testVehicle = new Vehicle(testDriver, "KA-01-" + suffix.toUpperCase(), VehicleType.CAR);
        testVehicle = vehicleRepository.saveAndFlush(testVehicle);

        testLot = new ParkingLot(
                "Kafka Lot " + suffix,
                "MG Road, Bengaluru",
                "Bengaluru",
                "Karnataka",
                "560001",
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946")
        );
        testLot.setOpeningTime(LocalTime.of(0, 0));
        testLot.setClosingTime(LocalTime.of(23, 59));
        testLot = parkingLotRepository.saveAndFlush(testLot);

        testFloor = new ParkingFloor(testLot, 1, "Level 1");
        testFloor = parkingFloorRepository.saveAndFlush(testFloor);

        testSlot = new ParkingSlot(testFloor, "K-" + suffix, SlotType.REGULAR, SlotStatus.AVAILABLE);
        testSlot = parkingSlotRepository.saveAndFlush(testSlot);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        outboxPublisher.setOutboxEnabled(true);
    }

    private void cleanDatabase() {
        notificationRepository.deleteAll();
        processedEventRepository.deleteAll();
        outboxEventRepository.deleteAll();
        bookingRepository.deleteAll();
        vehicleRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        parkingFloorRepository.deleteAll();
        parkingLotRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsDriver() {
        com.smartpark.security.UserPrincipal principal = com.smartpark.security.UserPrincipal.fromUser(testDriver);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("1. Direct Kafka producer publishes and NotificationConsumer processes message")
    void testDirectKafkaProducerAndConsumer() throws Exception {
        UUID eventId = UUID.randomUUID();
        BookingKafkaEvent event = new BookingKafkaEvent(
                eventId,
                "BOOKING_CREATED",
                8888L,
                testDriver.getId(),
                testLot.getId(),
                testFloor.getId(),
                testSlot.getId(),
                testSlot.getSlotNumber(),
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(3)),
                Instant.now(),
                1
        );

        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(bookingEventsTopic, String.valueOf(event.getBookingId()), payload).get(5, TimeUnit.SECONDS);

        // Await consumption by NotificationConsumer
        awaitCondition(() -> processedEventRepository.existsById(eventId), Duration.ofSeconds(10));

        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        Optional<Notification> notification = notificationRepository.findByEventId(eventId);
        assertThat(notification).isPresent();
        assertThat(notification.get().getUser().getId()).isEqualTo(testDriver.getId());
        assertThat(notification.get().getMessage()).contains("8888");
        assertThat(notification.get().getType()).isEqualTo("BOOKING_CREATED");
    }

    @Test
    @DisplayName("2. Booking creation atomically inserts an outbox event in PostgreSQL")
    void testBookingCreationInsertsOutboxEvent() {
        authenticateAsDriver();

        LocalDate tomorrow = LocalDate.now(istZone).plusDays(1);
        Instant start = tomorrow.atTime(10, 0).atZone(istZone).toInstant();
        Instant end = tomorrow.atTime(12, 0).atZone(istZone).toInstant();

        CreateBookingRequest request = new CreateBookingRequest(
                testVehicle.getId(),
                testSlot.getId(),
                start,
                end
        );

        var response = bookingService.createBooking(request);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateIdOrderByCreatedAtAsc(response.getId());
        assertThat(outboxEvents).hasSize(1);

        OutboxEvent outboxEvent = outboxEvents.get(0);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("BOOKING");
        assertThat(outboxEvent.getEventType()).isEqualTo("BOOKING_CREATED");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(response.getId());
        assertThat(outboxEvent.getPublishedAt()).isNull(); // Unpublished before poller runs
        assertThat(outboxEvent.getAttempts()).isEqualTo(0);
        assertThat(outboxEvent.getLastError()).isNull();
        assertThat(outboxEvent.getPayload()).contains(String.valueOf(response.getId()));
    }

    @Test
    @DisplayName("3. Outbox publisher sends unpublished events to Kafka and sets published_at timestamp")
    void testOutboxPublisherSendsToKafkaAndSetsPublishedAt() throws Exception {
        UUID eventId = UUID.randomUUID();
        BookingKafkaEvent event = new BookingKafkaEvent(
                eventId,
                "BOOKING_CONFIRMED",
                9999L,
                testDriver.getId(),
                testLot.getId(),
                testFloor.getId(),
                testSlot.getId(),
                testSlot.getSlotNumber(),
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)),
                Instant.now(),
                1
        );

        outboxService.saveEvent(eventId, "BOOKING", 9999L, "BOOKING_CONFIRMED", event);

        OutboxEvent initial = outboxEventRepository.findByEventId(eventId).orElseThrow();
        assertThat(initial.getPublishedAt()).isNull();

        int published = outboxPublisher.publishPendingEvents();
        assertThat(published).isGreaterThanOrEqualTo(1);

        OutboxEvent publishedEvent = outboxEventRepository.findByEventId(eventId).orElseThrow();
        assertThat(publishedEvent.getPublishedAt()).isNotNull();
        assertThat(publishedEvent.getLastError()).isNull();

        // Verify consumer received and persisted the event
        awaitCondition(() -> processedEventRepository.existsById(eventId), Duration.ofSeconds(10));
        assertThat(notificationRepository.findByEventId(eventId)).isPresent();
    }

    @Test
    @DisplayName("4. Consumer idempotency: duplicate deliveries are ignored and do not create duplicate notifications")
    void testIdempotentConsumerIgnoresDuplicates() throws Exception {
        UUID eventId = UUID.randomUUID();
        BookingKafkaEvent event = new BookingKafkaEvent(
                eventId,
                "BOOKING_COMPLETED",
                7777L,
                testDriver.getId(),
                testLot.getId(),
                testFloor.getId(),
                testSlot.getId(),
                testSlot.getSlotNumber(),
                Instant.now(),
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now(),
                1
        );

        String payload = objectMapper.writeValueAsString(event);

        // Send first message
        kafkaTemplate.send(bookingEventsTopic, "7777", payload).get(5, TimeUnit.SECONDS);
        awaitCondition(() -> processedEventRepository.existsById(eventId), Duration.ofSeconds(10));

        List<Notification> notificationsFirst = notificationRepository.findByUserIdOrderByCreatedAtDesc(testDriver.getId());
        assertThat(notificationsFirst).hasSize(1);

        // Send duplicate message with identical eventId
        kafkaTemplate.send(bookingEventsTopic, "7777", payload).get(5, TimeUnit.SECONDS);

        // Brief pause to allow consumer to receive duplicate
        Thread.sleep(2000);

        List<Notification> notificationsSecond = notificationRepository.findByUserIdOrderByCreatedAtDesc(testDriver.getId());
        assertThat(notificationsSecond).hasSize(1); // Still exactly 1, duplicate rejected
    }

    @Test
    @DisplayName("5. Pre-processed eventId is immediately skipped by consumer")
    void testPreProcessedEventIdSkipped() throws Exception {
        UUID eventId = UUID.randomUUID();
        // Record eventId as already processed in database
        processedEventRepository.saveAndFlush(new ProcessedEvent(eventId, "BOOKING_CREATED"));

        BookingKafkaEvent event = new BookingKafkaEvent(
                eventId,
                "BOOKING_CREATED",
                6666L,
                testDriver.getId(),
                testLot.getId(),
                testFloor.getId(),
                testSlot.getId(),
                testSlot.getSlotNumber(),
                Instant.now(),
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now(),
                1
        );

        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(bookingEventsTopic, "6666", payload).get(5, TimeUnit.SECONDS);

        Thread.sleep(2000);

        // Assert notification was NOT created because consumer skipped already-processed eventId
        Optional<Notification> notification = notificationRepository.findByEventId(eventId);
        assertThat(notification).isEmpty();
    }

    @Test
    @DisplayName("6. Transaction rollback produces NO outbox event in PostgreSQL")
    void testTransactionRollbackProducesNoOutboxEvent() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        long beforeCount = outboxEventRepository.count();

        assertThatThrownBy(() -> txTemplate.execute(status -> {
            outboxService.saveEvent("BOOKING", 12345L, "BOOKING_CREATED", Map.of("test", "fail"));
            throw new RuntimeException("Simulated business transaction failure");
        })).isInstanceOf(RuntimeException.class);

        long afterCount = outboxEventRepository.count();
        assertThat(afterCount).isEqualTo(beforeCount);

        int published = outboxPublisher.publishPendingEvents();
        assertThat(published).isEqualTo(0);
    }

    @Test
    @DisplayName("7. Kafka send failure preserves outbox event with incremented attempts and error message")
    void testKafkaSendFailurePreservesOutboxEvent() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent outboxEvent = new OutboxEvent(eventId, "BOOKING", 5555L, "BOOKING_CREATED", "{\"test\":1}");
        outboxEventRepository.saveAndFlush(outboxEvent);

        // Create failing publisher with mock KafkaTemplate
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> failingKafkaTemplate = mock(KafkaTemplate.class);
        when(failingKafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new org.apache.kafka.common.errors.TimeoutException("Kafka broker simulated timeout")));

        OutboxPublisher failingPublisher = new OutboxPublisher(outboxEventRepository, failingKafkaTemplate);
        int published = failingPublisher.publishPendingEvents();

        assertThat(published).isEqualTo(0);

        OutboxEvent saved = outboxEventRepository.findByEventId(eventId).orElseThrow();
        assertThat(saved.getPublishedAt()).isNull(); // Must remain unpublished for future retry
        assertThat(saved.getAttempts()).isGreaterThanOrEqualTo(1);
        assertThat(saved.getLastError()).contains("Kafka broker simulated timeout");
    }

    @Test
    @DisplayName("8. Poison pill malformed message is retried and forwarded to Dead Letter Topic (DLT)")
    void testPoisonPillRoutesToDlt() {
        String poisonPill = "{ malformed_invalid_json: true ";

        // Set up verifier consumer for DLT
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-verifier-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> dltConsumer = new KafkaConsumer<>(props)) {
            dltConsumer.subscribe(Collections.singletonList(bookingEventsDltTopic));

            // Send malformed message to primary booking events topic
            kafkaTemplate.send(bookingEventsTopic, "poison-key", poisonPill);

            // Await delivery to DLT topic after 2 retries (DefaultErrorHandler backoff)
            boolean dltReceived = false;
            Instant deadline = Instant.now().plusSeconds(12);

            while (Instant.now().isBefore(deadline)) {
                ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().contains("malformed_invalid_json")) {
                        dltReceived = true;
                        break;
                    }
                }
                if (dltReceived) {
                    break;
                }
            }

            assertThat(dltReceived)
                    .as("Poison pill message should have been forwarded to " + bookingEventsDltTopic)
                    .isTrue();
        }
    }

    private void awaitCondition(java.util.function.BooleanSupplier condition, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
