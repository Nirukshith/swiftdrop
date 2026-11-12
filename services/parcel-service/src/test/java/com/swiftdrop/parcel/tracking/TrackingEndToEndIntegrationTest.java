package com.swiftdrop.parcel.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.common.security.dev.DevJwtTokenMinter;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 4 integration (Issue #164): the whole path from a real parcel status change to the
 * tracking pages, with nothing hand-built in between.
 *
 * M1's internal status endpoint changes the parcel, M1's ParcelKafkaEventListener publishes
 * parcel.status-changed.v1 after commit to a real Kafka broker, and the tracking consumers
 * store the change and push it to SSE clients connected over real HTTP. Authenticated calls use
 * signed bearer tokens checked by parcel-service's own security chain (dev profile key).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "swiftdrop.tracking.rate-limit.enabled=false",
        "swiftdrop.tracking.stream.heartbeat=300ms",
})
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackingEndToEndIntegrationTest {

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

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String OWNER = "c1a2b3c4-0000-4000-8000-0000000000d1";
    private static final String OTHER_CUSTOMER = "c1a2b3c4-0000-4000-8000-0000000000d2";
    private static final String COURIER = "c1a2b3c4-0000-4000-8000-0000000000d3";

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
    private ObjectMapper objectMapper;
    @Autowired
    private DevJwtTokenMinter tokens;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;

    private final HttpClient http = HttpClient.newHttpClient();
    private Parcel parcel;
    private String trackingNumber;

    @BeforeEach
    void setUp() {
        listeners.getListenerContainers().forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
        // A fresh number per test, so a late event from an earlier test cannot affect this one.
        trackingNumber = "SD-20261112-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        parcel = createParcel(trackingNumber);
    }

    /** A new customer parcel in CREATED, as M1's create endpoint stores it. */
    private Parcel createParcel(String number) {
        Parcel p = new Parcel();
        p.setTrackingNumber(number);
        p.setStatus(ParcelStatus.CREATED);
        p.setCustomerId(OWNER);
        p.setPickupAddress(new AddressSnapshot("45 Galle Road", null, "Colombo 03", "Colombo", "00300"));
        p.setDropoffAddress(new AddressSnapshot("12 Temple Road", "Flat 2", "Kandy", "Kandy", "20000"));
        p.setWeightKg(new BigDecimal("2.50"));
        p.setSize(ParcelSize.MEDIUM);
        p.setFee(new BigDecimal("750.00"));
        p.setRecipientName("Sunil Silva");
        p.setRecipientPhone("+94771234567");
        p.setNotes("Gate code 4321");
        Parcel saved = parcelRepository.saveAndFlush(p);
        statusHistoryRepository.saveAndFlush(
                new StatusHistory(saved.getId(), ParcelStatus.CREATED, ParcelStatus.CREATED, OWNER, "Parcel order created"));
        return saved;
    }

    // ------------------------------------------------------------------ helpers

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String streamUrl() {
        return url("/api/v1/tracking/" + trackingNumber + "/stream");
    }

    /** Assigns the courier through M1's internal endpoint (moves the sequence, keeps the status). */
    private void assignCourier() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url("/internal/v1/parcels/" + parcel.getId() + "/assignment")))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"courierId\": \"" + COURIER + "\", \"assignedBy\": \"dispatcher-1\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    /** A real status change through M1's internal endpoint (state machine, history, Kafka after commit). */
    private long changeStatus(ParcelStatus from, ParcelStatus to) throws Exception {
        String body = "{\"status\": \"" + to + "\", \"expectedCurrentStatus\": \"" + from + "\", \"courierId\": \"" + COURIER + "\""
                + (to == ParcelStatus.DELIVERED ? ", \"proof\": {\"recipientName\": \"Sunil Silva\", \"proofPhotoPath\": \"proofs/p.jpg\"}" : "")
                + (to == ParcelStatus.FAILED ? ", \"reason\": \"Recipient not home, gate code 4321\"" : "")
                + "}";
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url("/internal/v1/parcels/" + parcel.getId() + "/status")))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return objectMapper.readTree(response.body()).get("sequence").asLong();
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path))).GET();
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Publishes an old or repeated event for this parcel the way M1 would (to test stale handling). */
    private void publishStale(long sequence, ParcelStatus from, ParcelStatus to) throws Exception {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), "PARCEL_STATUS_CHANGED",
                Instant.parse("2026-11-12T08:00:00Z"), sequence, parcel.getId(), trackingNumber,
                from.name(), to.name(), COURIER, null, null, OWNER, null);
        kafkaTemplate.send(TrackingKafkaListeners.TOPIC, parcel.getId(), objectMapper.writeValueAsString(envelope)).get();
    }

    private List<Long> storedSequences() {
        return jdbc.queryForList("SELECT sequence FROM tracking_event WHERE tracking_number = ? ORDER BY sequence",
                Long.class, trackingNumber);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition not met within %s", WAIT).isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    /** Checks one frame against parcel-read.yaml PublicTrackingEvent and returns its body. */
    private JsonNode assertStatusEvent(SseTestClient.Frame frame, long sequence, ParcelStatus status) throws Exception {
        assertThat(frame).as("expected status event %d (%s)", sequence, status).isNotNull();
        assertThat(frame.event()).isEqualTo("status");
        assertThat(frame.id()).isEqualTo(Long.toString(sequence));
        JsonNode data = objectMapper.readTree(frame.data());
        assertThat(data.size()).as("exactly the four public fields").isEqualTo(4);
        assertThat(data.get("trackingNumber").asText()).isEqualTo(trackingNumber);
        assertThat(data.get("sequence").asLong()).isEqualTo(sequence);
        assertThat(data.get("status").asText()).isEqualTo(status.name());
        assertThat(Instant.parse(data.get("occurredAt").asText())).isBefore(Instant.now().plusSeconds(5));
        assertThat(frame.data()).doesNotContain(OWNER, COURIER, parcel.getId(), "Sunil", "Temple", "+9477", "Gate code", "proofs/");
        return data;
    }

    // ------------------------------------------------------------------ event to tracking

    @Test
    @DisplayName("Real status changes reach an open stream live, in order, in the contract format, and are stored")
    void realStatusChangesReachTheStream() throws Exception {
        try (SseTestClient client = SseTestClient.connect(streamUrl(), null)) {
            assertThat(client.status).isEqualTo(200);
            assertThat(client.nextFrame(WAIT).retry()).isEqualTo("5000");
            // Current state at connect: CREATED with the parcel's sequence (1 after creation)
            long start = parcelRepository.findById(parcel.getId()).orElseThrow().getSequence();
            assertStatusEvent(client.nextEvent(WAIT), start, ParcelStatus.CREATED);

            assignCourier(); // moves the sequence; same status, so no new row on the page
            long pickedUp = changeStatus(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
            long inTransit = changeStatus(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
            long outForDelivery = changeStatus(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
            assertThat(List.of(pickedUp, inTransit, outForDelivery)).isSorted().doesNotHaveDuplicates();

            assertStatusEvent(client.nextEvent(WAIT), pickedUp, ParcelStatus.PICKED_UP);
            assertStatusEvent(client.nextEvent(WAIT), inTransit, ParcelStatus.IN_TRANSIT);
            assertStatusEvent(client.nextEvent(WAIT), outForDelivery, ParcelStatus.OUT_FOR_DELIVERY);

            long delivered = changeStatus(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED);
            assertStatusEvent(client.nextEvent(WAIT), delivered, ParcelStatus.DELIVERED);
            assertThat(client.nextEvent(Duration.ofSeconds(3))).as("stream ends after DELIVERED").isNull();
        }
        // Every status change was stored for replay (the assignment is not a status change).
        await(() -> storedSequences().size() == 4);
    }

    @Test
    @DisplayName("Stale and repeated events never reach the stream or move it backwards")
    void staleEventsAreIgnored() throws Exception {
        try (SseTestClient client = SseTestClient.connect(streamUrl(), null)) {
            client.nextFrame(WAIT); // retry
            client.nextEvent(WAIT); // current state
            long pickedUp = changeStatus(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
            long inTransit = changeStatus(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
            assertStatusEvent(client.nextEvent(WAIT), pickedUp, ParcelStatus.PICKED_UP);
            assertStatusEvent(client.nextEvent(WAIT), inTransit, ParcelStatus.IN_TRANSIT);

            // A late redelivery of an older change and a repeat of the latest one arrive.
            publishStale(pickedUp, ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
            publishStale(inTransit, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
            // Then the next real change.
            long outForDelivery = changeStatus(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);

            // The client gets only the new change: nothing older, nothing twice.
            assertStatusEvent(client.nextEvent(WAIT), outForDelivery, ParcelStatus.OUT_FOR_DELIVERY);
            assertThat(client.nextEvent(Duration.ofSeconds(1))).isNull();
        }
    }

    @Test
    @DisplayName("After a disconnect, reconnecting with Last-Event-ID replays only the real changes that were missed")
    void reconnectAfterRealChanges() throws Exception {
        long pickedUp;
        try (SseTestClient client = SseTestClient.connect(streamUrl(), null)) {
            client.nextFrame(WAIT);
            client.nextEvent(WAIT);
            pickedUp = changeStatus(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
            assertStatusEvent(client.nextEvent(WAIT), pickedUp, ParcelStatus.PICKED_UP);
        } // the client goes away (network drop, tab closed)

        long inTransit = changeStatus(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
        long outForDelivery = changeStatus(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
        await(() -> storedSequences().containsAll(List.of(inTransit, outForDelivery)));

        try (SseTestClient again = SseTestClient.connect(streamUrl(), Long.toString(pickedUp))) {
            again.nextFrame(WAIT);
            assertStatusEvent(again.nextEvent(WAIT), inTransit, ParcelStatus.IN_TRANSIT);
            assertStatusEvent(again.nextEvent(WAIT), outForDelivery, ParcelStatus.OUT_FOR_DELIVERY);
            assertThat(again.nextEvent(Duration.ofMillis(800))).as("nothing twice").isNull();

            long delivered = changeStatus(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED);
            assertStatusEvent(again.nextEvent(WAIT), delivered, ParcelStatus.DELIVERED);
        }
    }

    // ------------------------------------------------------------------ what each page reads

    @Test
    @DisplayName("Public lookup shows the real status and times from the status changes, masked, without a login")
    void publicLookupShowsRealMaskedTimeline() throws Exception {
        changeStatus(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
        changeStatus(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);

        HttpResponse<String> response = get("/api/v1/tracking/" + trackingNumber, null);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("status").asText()).isEqualTo("IN_TRANSIT");
        assertThat(body.get("recipientNameMasked").asText()).isEqualTo("S*** S***");
        assertThat(body.get("dropoffDistrict").asText()).isEqualTo("Kandy");
        JsonNode timeline = body.get("timeline");
        assertThat(timeline).hasSize(3);
        assertThat(timeline.get(0).get("status").asText()).isEqualTo("CREATED");
        assertThat(timeline.get(2).get("status").asText()).isEqualTo("IN_TRANSIT");
        // Real times of the stored changes, oldest first
        Instant first = Instant.parse(timeline.get(0).get("occurredAt").asText());
        Instant last = Instant.parse(timeline.get(2).get("occurredAt").asText());
        assertThat(last).isAfterOrEqualTo(first).isBefore(Instant.now().plusSeconds(5));
        assertThat(body.get("lastUpdatedAt").asText()).isEqualTo(timeline.get(2).get("occurredAt").asText());
        assertThat(response.body()).doesNotContain("Temple Road", "Flat 2", "20000", "Galle Road", "+9477", "750",
                "Gate code", "Sunil", OWNER, COURIER, parcel.getId());
    }

    @Test
    @DisplayName("History with a signed token: owner 200 with full detail, another customer 403, no token 401")
    void historyAccessWithRealTokens() throws Exception {
        assignCourier();
        changeStatus(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
        changeStatus(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
        String path = "/api/v1/parcels/" + parcel.getId() + "/history";

        HttpResponse<String> owner = get(path, tokens.mintCustomerToken(OWNER));
        assertThat(owner.statusCode()).as(owner.body()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(owner.body());
        assertThat(body.get("currentStatus").asText()).isEqualTo("IN_TRANSIT");
        // One row per status change: M1's courier-assignment row is not shown as a second "Created"
        assertThat(body.get("entries")).hasSize(3);
        assertThat(body.get("entries").get(0).get("reason").asText()).isEqualTo("Parcel order created");
        assertThat(body.get("entries").get(0).get("fromStatus").isNull()).isTrue();
        assertThat(owner.body()).doesNotContain(COURIER);

        assertThat(get(path, tokens.mintCustomerToken(OTHER_CUSTOMER)).statusCode()).isEqualTo(403);
        assertThat(get(path, tokens.mintCourierToken("c1a2b3c4-0000-4000-8000-0000000000d9")).statusCode()).isEqualTo(403);
        assertThat(get(path, tokens.mintCourierToken(COURIER)).statusCode()).as("assigned courier").isEqualTo(200);
        assertThat(get(path, tokens.mintAdminToken("admin-1")).statusCode()).isEqualTo(200);
        assertThat(get(path, null).statusCode()).isEqualTo(401);
        assertThat(get(path, "not-a-real-token").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Public tracking needs no login; a stale token sent by a signed-in browser is ignored, not rejected")
    void publicTrackingNeedsNoLogin() throws Exception {
        try (SseTestClient client = SseTestClient.connect(streamUrl(), null)) {
            assertThat(client.status).isEqualTo(200);
        }
        // The Angular interceptor adds the bearer token to every call while signed in. An expired or
        // broken token must not turn the public page into a 401, and it gives no extra data either.
        HttpResponse<String> withBadToken = get("/api/v1/tracking/" + trackingNumber, "expired.or.broken");
        assertThat(withBadToken.statusCode()).isEqualTo(200);
        HttpResponse<String> withOwnerToken = get("/api/v1/tracking/" + trackingNumber, tokens.mintCustomerToken(OWNER));
        assertThat(withOwnerToken.body()).as("same masked view for everyone").isEqualTo(withBadToken.body());
        assertThat(withOwnerToken.body()).doesNotContain("Temple Road", "+9477", OWNER);
        // An unknown number gets the contract's 404 JSON, the same as the public lookup
        HttpResponse<String> unknown = get("/api/v1/tracking/SD-20991231-NOPE00/stream", null);
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(unknown.body()).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
    }
}
