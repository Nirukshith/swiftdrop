package com.swiftdrop.parcel.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import com.swiftdrop.parcel.parcel.PricingQuoteClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Failure and retry behaviour of the tracking history writer (topics.md section 2,
 * TrackingKafkaConfig): a failing store is retried with back-off (5 attempts in total) and then
 * the original message goes to parcel.status-changed.v1.dlt; an unusable message goes to the
 * DLT at once. Real Kafka and PostgreSQL in Testcontainers; the store is a spy so a database
 * failure can be simulated for one event only.
 */
@SpringBootTest(properties = "swiftdrop.tracking.rate-limit.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackingRetryIntegrationTest {

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

        @Bean
        NewTopic statusChangedDeadLetterTopic() {
            return TopicBuilder.name(DLT).partitions(1).replicas(1).build();
        }
    }

    private static final String DLT = TrackingKafkaListeners.TOPIC + TrackingKafkaConfig.DLT_SUFFIX;
    /** Back-off 1 s + 2 s + 4 s + 8 s before the record is given up, plus margin. */
    private static final Duration GIVE_UP = Duration.ofSeconds(40);

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private KafkaListenerEndpointRegistry listeners;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private TrackingEventStore store;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;

    private KafkaConsumer<String, String> deadLetters;

    @BeforeEach
    void setUp() {
        listeners.getListenerContainers().forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
        deadLetters = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        deadLetters.subscribe(List.of(DLT));
    }

    @AfterEach
    void tearDown() {
        deadLetters.close();
    }

    // ------------------------------------------------------------------ helpers

    /** A status-changed event exactly as M1's ParcelKafkaEventListener sends it. */
    private String event(String eventId, String trackingNumber) throws Exception {
        EventEnvelope envelope = new EventEnvelope(eventId, "PARCEL_STATUS_CHANGED", Instant.parse("2026-11-13T09:00:00Z"),
                2L, UUID.randomUUID().toString(), trackingNumber, "CREATED", ParcelStatus.PICKED_UP.name(),
                "courier-1", null, null, "customer-1", null);
        return objectMapper.writeValueAsString(envelope);
    }

    private static String trackingNumber() {
        return "SD-20261113-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private void send(String key, String payload) throws Exception {
        kafkaTemplate.send(TrackingKafkaListeners.TOPIC, key, payload).get();
    }

    private int stored(String eventId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tracking_event WHERE event_id = ?", Integer.class, eventId);
    }

    /** Makes the store fail for this event only, the given number of times (forever when negative). */
    private AtomicInteger failStoreFor(String eventId, int times) {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            int attempt = attempts.incrementAndGet();
            if (times < 0 || attempt <= times) {
                throw new DataAccessResourceFailureException("simulated database outage, attempt " + attempt);
            }
            return invocation.callRealMethod();
        }).when(store).save(argThat(change -> change != null && eventId.equals(change.eventId())));
        return attempts;
    }

    /** Dead letters whose value contains the given text, read for up to the given time. */
    private List<ConsumerRecord<String, String>> deadLettersContaining(String text, Duration within) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        long deadline = System.nanoTime() + within.toNanos();
        while (found.isEmpty() && System.nanoTime() < deadline) {
            deadLetters.poll(Duration.ofMillis(500)).forEach(record -> {
                if (record.value() != null && record.value().contains(text)) {
                    found.add(record);
                }
            });
        }
        return found;
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static void await(Duration within, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + within.toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition not met within %s", within).isLessThan(deadline);
            Thread.sleep(100);
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("A short database outage is retried: the change is stored exactly once and nothing reaches the DLT")
    void transientFailureIsRetried() throws Exception {
        String eventId = UUID.randomUUID().toString();
        AtomicInteger attempts = failStoreFor(eventId, 2);

        send("parcel-a", event(eventId, trackingNumber()));

        await(GIVE_UP, () -> stored(eventId) == 1);
        assertThat(attempts.get()).as("two failures, then success").isEqualTo(3);
        assertThat(deadLettersContaining(eventId, Duration.ofSeconds(3))).isEmpty();
    }

    @Test
    @DisplayName("A failure that does not clear is retried 5 times in total, then the original message goes to the DLT")
    void permanentFailureGoesToDeadLetterTopic() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = event(eventId, trackingNumber());
        AtomicInteger attempts = failStoreFor(eventId, -1);
        long sentAt = System.nanoTime();

        send("parcel-b", payload);

        List<ConsumerRecord<String, String>> dead = deadLettersContaining(eventId, GIVE_UP);
        long seconds = Duration.ofNanos(System.nanoTime() - sentAt).toSeconds();
        assertThat(dead).hasSize(1);
        ConsumerRecord<String, String> record = dead.get(0);
        assertThat(record.value()).as("original payload, unchanged").isEqualTo(payload);
        assertThat(record.key()).isEqualTo("parcel-b");
        assertThat(header(record, "kafka_dlt-original-topic")).isEqualTo(TrackingKafkaListeners.TOPIC);
        assertThat(header(record, "kafka_dlt-exception-cause-fqcn")).isEqualTo(DataAccessResourceFailureException.class.getName());
        assertThat(attempts.get()).as("1 attempt + 4 retries").isEqualTo(5);
        assertThat(seconds).as("back-off of 1 s + 2 s + 4 s + 8 s before giving up").isGreaterThanOrEqualTo(14);
        assertThat(stored(eventId)).isZero();
    }

    @Test
    @DisplayName("An unusable message goes to the DLT at once, without retries, and later events are still stored")
    void unusableMessageGoesStraightToDeadLetterTopic() throws Exception {
        String marker = "broken-" + UUID.randomUUID();
        String broken = "{\"eventId\": \"" + marker + "\", \"eventType\": \"PARCEL_STATUS_CHANGED\"}"; // no parcelId, sequence ...
        long sentAt = System.nanoTime();

        send("parcel-c", broken);

        List<ConsumerRecord<String, String>> dead = deadLettersContaining(marker, Duration.ofSeconds(20));
        assertThat(dead).hasSize(1);
        assertThat(dead.get(0).value()).isEqualTo(broken);
        assertThat(header(dead.get(0), "kafka_dlt-exception-cause-fqcn")).isEqualTo(InvalidTrackingEventException.class.getName());
        assertThat(Duration.ofNanos(System.nanoTime() - sentAt)).as("no back-off for unusable messages").isLessThan(Duration.ofSeconds(8));
        verify(store, never()).save(argThat(change -> change != null && marker.equals(change.eventId())));

        // The consumer is not stuck behind the broken message.
        String next = UUID.randomUUID().toString();
        send("parcel-c", event(next, trackingNumber()));
        await(Duration.ofSeconds(20), () -> stored(next) == 1);
        verify(store, atLeast(1)).save(argThat(change -> change != null && next.equals(change.eventId())));
    }
}
