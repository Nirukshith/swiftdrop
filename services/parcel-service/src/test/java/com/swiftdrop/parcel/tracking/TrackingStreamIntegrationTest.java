package com.swiftdrop.parcel.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.parcel.parcel.AddressSnapshot;
import com.swiftdrop.parcel.parcel.Parcel;
import com.swiftdrop.parcel.parcel.ParcelRepository;
import com.swiftdrop.parcel.parcel.ParcelSize;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import com.swiftdrop.parcel.parcel.PricingQuoteClient;
import com.swiftdrop.parcel.parcel.StatusHistory;
import com.swiftdrop.parcel.parcel.StatusHistoryRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP SSE clients against a running parcel-service, with Postgres and Kafka in
 * Testcontainers. Events are published to parcel.status-changed.v1 exactly as M1's
 * ParcelKafkaEventListener does (EventEnvelope JSON, key = parcelId).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "swiftdrop.tracking.rate-limit.enabled=false",
        "swiftdrop.tracking.stream.heartbeat=300ms",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackingStreamIntegrationTest {

    // Started once and left to Testcontainers to remove at JVM exit, so the Spring context (and its
    // Kafka consumers) is closed by @DirtiesContext while the broker is still reachable.
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    static {
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @TestConfiguration
    static class Topics {
        @Bean
        NewTopic statusChangedTopic() {
            return TopicBuilder.name(TrackingKafkaListeners.TOPIC).partitions(1).replicas(1).build();
        }
    }

    private static final Duration WAIT = Duration.ofSeconds(15);
    private String TN;
    private String OTHER_TN;

    @LocalServerPort
    private int port;

    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private StatusHistoryRepository statusHistoryRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private KafkaListenerEndpointRegistry listeners;
    @Autowired
    private TrackingEmitterRegistry registry;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;

    private Parcel parcel;
    private Parcel otherParcel;

    @BeforeEach
    void setUp() {
        listeners.getListenerContainers().forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
        jdbc.update("DELETE FROM tracking_event");
        statusHistoryRepository.deleteAll();
        parcelRepository.deleteAll();
        TN = randomTrackingNumber();
        OTHER_TN = randomTrackingNumber();
        parcel = saveParcel(TN, ParcelStatus.PICKED_UP, 3L);
        otherParcel = saveParcel(OTHER_TN, ParcelStatus.PICKED_UP, 3L);
    }

    /** A fresh number per test, so a late event from an earlier test cannot affect this one. */
    private static String randomTrackingNumber() {
        return "SD-20261104-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private Parcel saveParcel(String trackingNumber, ParcelStatus status, long sequence) {
        Parcel p = new Parcel();
        p.setTrackingNumber(trackingNumber);
        p.setStatus(status);
        p.setSequence(sequence);
        p.setCustomerId("c1a2b3c4-0000-4000-8000-0000000000b1");
        p.setCourierId("c1a2b3c4-0000-4000-8000-0000000000b3");
        p.setPickupAddress(new AddressSnapshot("45 Galle Road", null, "Colombo 03", "Colombo", "00300"));
        p.setDropoffAddress(new AddressSnapshot("12 Temple Road", null, "Kandy", "Kandy", "20000"));
        p.setWeightKg(new BigDecimal("2.50"));
        p.setSize(ParcelSize.MEDIUM);
        p.setFee(new BigDecimal("750.00"));
        p.setRecipientName("Sunil Silva");
        p.setRecipientPhone("+94771234567");
        Parcel saved = parcelRepository.saveAndFlush(p);
        StatusHistory created = new StatusHistory(saved.getId(), ParcelStatus.CREATED, ParcelStatus.CREATED, "cust", null);
        created.setCreatedAt(Instant.parse("2026-11-04T08:00:00Z"));
        StatusHistory current = new StatusHistory(saved.getId(), ParcelStatus.CREATED, status, "courier", null);
        current.setCreatedAt(Instant.parse("2026-11-04T09:00:00Z"));
        statusHistoryRepository.saveAll(List.of(created, current));
        return saved;
    }

    private String streamUrl(String trackingNumber) {
        return "http://localhost:" + port + "/api/v1/tracking/" + trackingNumber + "/stream";
    }

    /** Publishes like M1 does and returns the eventId. */
    private String publish(Parcel target, long sequence, ParcelStatus from, ParcelStatus to) throws Exception {
        return publish(UUID.randomUUID().toString(), target, sequence, from, to);
    }

    private String publish(String eventId, Parcel target, long sequence, ParcelStatus from, ParcelStatus to) throws Exception {
        EventEnvelope envelope = new EventEnvelope(eventId, "PARCEL_STATUS_CHANGED",
                Instant.parse("2026-11-04T10:00:00Z").plusSeconds(sequence), sequence, target.getId(),
                target.getTrackingNumber(), from.name(), to.name(), target.getCourierId(), null, null,
                target.getCustomerId(), "Gate code 1234");
        kafkaTemplate.send(TrackingKafkaListeners.TOPIC, target.getId(), objectMapper.writeValueAsString(envelope)).get();
        return eventId;
    }

    private int storedEvents(String trackingNumber) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tracking_event WHERE tracking_number = ?", Integer.class, trackingNumber);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition not met within %s", WAIT).isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private void assertEvent(SseTestClient.Frame frame, long sequence, ParcelStatus status) throws Exception {
        assertThat(frame).as("expected event %d", sequence).isNotNull();
        assertThat(frame.event()).isEqualTo("status");
        assertThat(frame.id()).isEqualTo(Long.toString(sequence));
        JsonNode data = objectMapper.readTree(frame.data());
        assertThat(data.get("sequence").asLong()).isEqualTo(sequence);
        assertThat(data.get("status").asText()).isEqualTo(status.name());
        assertThat(data.get("trackingNumber").asText()).isNotBlank();
        assertThat(data.get("occurredAt").asText()).isNotBlank();
        // Masked: exactly the four public fields
        assertThat(data.size()).isEqualTo(4);
        assertThat(frame.data()).doesNotContain("customer", "courier", "Gate code", "Sunil", "Temple", "+9477");
    }

    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("Connect gets retry and the current state; a committed status change then arrives live and is stored")
    void currentStateThenLiveUpdate() throws Exception {
        try (SseTestClient client = SseTestClient.connect(streamUrl(TN), null)) {
            assertThat(client.status).isEqualTo(200);
            assertThat(client.contentType).startsWith("text/event-stream");
            assertThat(client.cacheControl).contains("no-cache");
            assertThat(client.accelBuffering).isEqualTo("no");

            SseTestClient.Frame first = client.nextFrame(WAIT);
            assertThat(first.retry()).isEqualTo("5000");
            assertEvent(client.nextEvent(WAIT), 3, ParcelStatus.PICKED_UP);

            publish(parcel, 4, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
            assertEvent(client.nextEvent(WAIT), 4, ParcelStatus.IN_TRANSIT);
        }
        await(() -> storedEvents(TN) == 1);
    }

    @Test
    @DisplayName("Several clients of one parcel all receive the update; another parcel's events never reach them")
    void multipleClientsAndParcelIsolation() throws Exception {
        try (SseTestClient a1 = SseTestClient.connect(streamUrl(TN), null);
             SseTestClient a2 = SseTestClient.connect(streamUrl(TN), null);
             SseTestClient b = SseTestClient.connect(streamUrl(OTHER_TN), null)) {
            for (SseTestClient client : List.of(a1, a2, b)) {
                assertEvent(client.nextEvent(WAIT), 3, ParcelStatus.PICKED_UP);
            }
            assertThat(registry.openStreams(TN)).isEqualTo(2);

            publish(otherParcel, 4, ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED);
            publish(parcel, 4, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);

            assertEvent(a1.nextEvent(WAIT), 4, ParcelStatus.IN_TRANSIT);
            assertEvent(a2.nextEvent(WAIT), 4, ParcelStatus.IN_TRANSIT);
            assertEvent(b.nextEvent(WAIT), 4, ParcelStatus.CANCELLED);
        }
    }

    @Test
    @DisplayName("Reconnect with Last-Event-ID replays only the missed changes, in order, without duplicates")
    void reconnectReplaysMissedChanges() throws Exception {
        publish(parcel, 4, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
        publish(parcel, 5, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
        await(() -> storedEvents(TN) == 2);
        // M1 has committed the same changes on the parcel.
        jdbc.update("UPDATE parcels SET status = 'OUT_FOR_DELIVERY', sequence = 5 WHERE id = ?", parcel.getId());

        try (SseTestClient client = SseTestClient.connect(streamUrl(TN), "3")) {
            assertEvent(client.nextEvent(WAIT), 4, ParcelStatus.IN_TRANSIT);
            assertEvent(client.nextEvent(WAIT), 5, ParcelStatus.OUT_FOR_DELIVERY);
            assertThat(client.nextEvent(Duration.ofMillis(700))).as("no duplicate current state").isNull();
        }
        try (SseTestClient upToDate = SseTestClient.connect(streamUrl(TN), "5")) {
            assertThat(upToDate.nextEvent(Duration.ofMillis(700))).as("nothing missed").isNull();
            publish(parcel, 6, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED);
            assertEvent(upToDate.nextEvent(WAIT), 6, ParcelStatus.FAILED);
        }
    }

    @Test
    @DisplayName("A redelivered event (same eventId) is stored once and pushed once")
    void duplicateEventIsHandledOnce() throws Exception {
        try (SseTestClient client = SseTestClient.connect(streamUrl(TN), null)) {
            assertEvent(client.nextEvent(WAIT), 3, ParcelStatus.PICKED_UP);

            String eventId = publish(parcel, 4, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
            publish(eventId, parcel, 4, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
            publish(parcel, 5, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);

            assertEvent(client.nextEvent(WAIT), 4, ParcelStatus.IN_TRANSIT);
            assertEvent(client.nextEvent(WAIT), 5, ParcelStatus.OUT_FOR_DELIVERY);
        }
        await(() -> storedEvents(TN) == 2);
    }

    @Test
    @DisplayName("An unusable message does not stop tracking")
    void badMessageDoesNotBreakTracking() throws Exception {
        try (SseTestClient client = SseTestClient.connect(streamUrl(TN), null)) {
            assertEvent(client.nextEvent(WAIT), 3, ParcelStatus.PICKED_UP);

            kafkaTemplate.send(TrackingKafkaListeners.TOPIC, parcel.getId(), "{not json").get();
            publish(parcel, 4, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);

            assertEvent(client.nextEvent(WAIT), 4, ParcelStatus.IN_TRANSIT);
        }
        await(() -> storedEvents(TN) == 1);
    }

    @Test
    @DisplayName("DELIVERED ends the stream and frees the slot; a delivered parcel gets its state and the end")
    void terminalStatusEndsStream() throws Exception {
        try (SseTestClient client = SseTestClient.connect(streamUrl(TN), null)) {
            assertEvent(client.nextEvent(WAIT), 3, ParcelStatus.PICKED_UP);
            publish(parcel, 4, ParcelStatus.PICKED_UP, ParcelStatus.DELIVERED);
            assertEvent(client.nextEvent(WAIT), 4, ParcelStatus.DELIVERED);
            assertThat(client.awaitEnd(WAIT)).isTrue();
        }
        await(() -> registry.openStreams(TN) == 0);

        jdbc.update("UPDATE parcels SET status = 'DELIVERED', sequence = 4 WHERE id = ?", parcel.getId());
        try (SseTestClient late = SseTestClient.connect(streamUrl(TN), null)) {
            assertEvent(late.nextEvent(WAIT), 4, ParcelStatus.DELIVERED);
            assertThat(late.awaitEnd(WAIT)).isTrue();
        }
    }

    @Test
    @DisplayName("Heartbeats keep the stream alive; a client that leaves is removed")
    void heartbeatAndDisconnectCleanup() throws Exception {
        SseTestClient client = SseTestClient.connect(streamUrl(TN), null);
        assertEvent(client.nextEvent(WAIT), 3, ParcelStatus.PICKED_UP);

        SseTestClient.Frame frame;
        do {
            frame = client.nextFrame(WAIT);
            assertThat(frame).as("heartbeat").isNotNull();
        } while (frame.comment() == null);
        assertThat(frame.comment()).isEqualTo("heartbeat");
        assertThat(registry.openStreams(TN)).isEqualTo(1);

        client.close();
        await(() -> registry.openStreams(TN) == 0);
    }

    @Test
    @DisplayName("Unknown and malformed tracking numbers get the same 404 JSON body")
    void unknownTrackingNumber() throws Exception {
        for (String trackingNumber : List.of("SD-20990101-NOPE00", "not-a-number")) {
            try (SseTestClient client = SseTestClient.connect(streamUrl(trackingNumber), null)) {
                assertThat(client.status).isEqualTo(404);
                assertThat(client.contentType).startsWith("application/json");
                JsonNode body = objectMapper.readTree(client.readAll());
                assertThat(body.get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
                assertThat(body.get("message").asText()).isEqualTo("No parcel found for this tracking number");
            }
        }
        assertThat(registry.openStreams("SD-20990101-NOPE00")).isZero();
    }
}
