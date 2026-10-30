package com.swiftdrop.parcel.parcel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.parcel.address.AddressDto;
import com.swiftdrop.parcel.internal.ParcelStatusUpdateRequest;
import org.apache.kafka.clients.consumer.Consumer;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class ParcelEventPublishingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @DynamicPropertySource
    static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;

    private Consumer<String, String> consumer;

    private static final String TOPIC_CREATED = "parcel.created.v1";
    private static final String TOPIC_ASSIGNED = "parcel.assigned.v1";
    private static final String TOPIC_STATUS_CHANGED = "parcel.status-changed.v1";

    @BeforeEach
    void setUp() {
        when(pricingQuoteClient.fetchQuote(any(), any(), any(), any())).thenReturn(new BigDecimal("850.00"));

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(TOPIC_CREATED, TOPIC_ASSIGNED, TOPIC_STATUS_CHANGED));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    private List<ConsumerRecord<String, String>> pollForRecords(String topic, int expectedCount) {
        List<ConsumerRecord<String, String>> result = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline && result.size() < expectedCount) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> rec : records) {
                if (rec.topic().equals(topic)) {
                    result.add(rec);
                }
            }
        }
        return result;
    }

    private CreateCustomerParcelRequest createSampleRequest() {
        AddressDto pickup = new AddressDto(
                "10 Galle Road", "Bambalapitiya", "Colombo", "Colombo", "00400"
        );
        AddressDto dropoff = new AddressDto(
                "55 Peradeniya Road", null, "Kandy", "Kandy", "20000"
        );
        return new CreateCustomerParcelRequest(
                pickup, dropoff, new BigDecimal("3.50"), ParcelSize.MEDIUM, "Kamal Gunaratne", "+94771122334", "Handle with care"
        );
    }

    @Test
    @DisplayName("Should publish parcel.created.v1 event with sequence 1 on parcel creation")
    void shouldPublishParcelCreatedEvent() throws Exception {
        String customerId = UUID.randomUUID().toString();
        ParcelResponse response = parcelService.createCustomerParcel(customerId, createSampleRequest());

        List<ConsumerRecord<String, String>> records = pollForRecords(TOPIC_CREATED, 1);
        assertThat(records).isNotEmpty();

        ConsumerRecord<String, String> record = records.stream()
                .filter(r -> r.key().equals(response.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Record not found for parcel " + response.id()));

        assertThat(record.key()).isEqualTo(response.id());
        EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);

        assertThat(envelope.eventId()).isNotBlank();
        assertThat(envelope.eventType()).isEqualTo("PARCEL_CREATED");
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.sequence()).isEqualTo(1L);
        assertThat(envelope.parcelId()).isEqualTo(response.id());
        assertThat(envelope.trackingNumber()).isEqualTo(response.trackingNumber());
        assertThat(envelope.fromStatus()).isEqualTo("NONE");
        assertThat(envelope.toStatus()).isEqualTo("CREATED");
        assertThat(envelope.courierId()).isNull();
        assertThat(envelope.previousCourierId()).isNull();
        assertThat(envelope.customerId()).isEqualTo(customerId);
    }

    @Test
    @DisplayName("Should publish parcel.assigned.v1 event with sequence 2 and previousCourierId null on initial assignment")
    void shouldPublishParcelAssignedEventOnInitialAssignment() throws Exception {
        String customerId = UUID.randomUUID().toString();
        ParcelResponse created = parcelService.createCustomerParcel(customerId, createSampleRequest());
        // Drain creation event
        pollForRecords(TOPIC_CREATED, 1);

        String courierId = UUID.randomUUID().toString();
        parcelService.assignCourier(created.id(), courierId, "dispatcher-01");

        List<ConsumerRecord<String, String>> records = pollForRecords(TOPIC_ASSIGNED, 1);
        ConsumerRecord<String, String> record = records.stream()
                .filter(r -> r.key().equals(created.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Record not found for parcel " + created.id()));

        EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
        assertThat(envelope.eventType()).isEqualTo("PARCEL_ASSIGNED");
        assertThat(envelope.sequence()).isEqualTo(2L);
        assertThat(envelope.parcelId()).isEqualTo(created.id());
        assertThat(envelope.courierId()).isEqualTo(courierId);
        assertThat(envelope.previousCourierId()).isNull();
        assertThat(envelope.fromStatus()).isEqualTo("CREATED");
        assertThat(envelope.toStatus()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("Should publish parcel.assigned.v1 with populated previousCourierId on reassignment per ADR 005")
    void shouldPublishParcelAssignedEventWithPreviousCourierOnReassignment() throws Exception {
        String customerId = UUID.randomUUID().toString();
        ParcelResponse created = parcelService.createCustomerParcel(customerId, createSampleRequest());
        String courierA = UUID.randomUUID().toString();
        parcelService.assignCourier(created.id(), courierA, "dispatcher-01");

        // Drain first assignment
        pollForRecords(TOPIC_ASSIGNED, 1);

        String courierB = UUID.randomUUID().toString();
        parcelService.assignCourier(created.id(), courierB, "dispatcher-01", true);

        List<ConsumerRecord<String, String>> records = pollForRecords(TOPIC_ASSIGNED, 1);
        ConsumerRecord<String, String> record = records.stream()
                .filter(r -> r.key().equals(created.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Record not found for parcel " + created.id()));

        EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
        assertThat(envelope.eventType()).isEqualTo("PARCEL_ASSIGNED");
        assertThat(envelope.sequence()).isEqualTo(3L);
        assertThat(envelope.courierId()).isEqualTo(courierB);
        assertThat(envelope.previousCourierId()).isEqualTo(courierA);
    }

    @Test
    @DisplayName("Should publish parcel.status-changed.v1 event across lifecycle transitions")
    void shouldPublishStatusChangedEvents() throws Exception {
        String customerId = UUID.randomUUID().toString();
        ParcelResponse created = parcelService.createCustomerParcel(customerId, createSampleRequest());
        String courierId = UUID.randomUUID().toString();
        parcelService.assignCourier(created.id(), courierId, "dispatcher-01");

        // Drain earlier events
        pollForRecords(TOPIC_CREATED, 1);
        pollForRecords(TOPIC_ASSIGNED, 1);

        // Transition: CREATED -> PICKED_UP
        parcelService.updateStatus(
                created.id(),
                new ParcelStatusUpdateRequest(ParcelStatus.PICKED_UP, ParcelStatus.CREATED, courierId, null, null, null),
                courierId
        );

        List<ConsumerRecord<String, String>> records = pollForRecords(TOPIC_STATUS_CHANGED, 1);
        ConsumerRecord<String, String> record = records.stream()
                .filter(r -> r.key().equals(created.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Record not found for parcel " + created.id()));

        EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
        assertThat(envelope.eventType()).isEqualTo("PARCEL_STATUS_CHANGED");
        assertThat(envelope.sequence()).isEqualTo(3L);
        assertThat(envelope.fromStatus()).isEqualTo("CREATED");
        assertThat(envelope.toStatus()).isEqualTo("PICKED_UP");
        assertThat(envelope.courierId()).isEqualTo(courierId);
    }

    @Test
    @DisplayName("Should publish parcel.status-changed.v1 on cancellation before pickup")
    void shouldPublishStatusChangedEventOnCancellation() throws Exception {
        String customerId = UUID.randomUUID().toString();
        ParcelResponse created = parcelService.createCustomerParcel(customerId, createSampleRequest());
        pollForRecords(TOPIC_CREATED, 1);

        parcelService.cancelParcel(created.id(), customerId, "Duplicate delivery order");

        List<ConsumerRecord<String, String>> records = pollForRecords(TOPIC_STATUS_CHANGED, 1);
        ConsumerRecord<String, String> record = records.stream()
                .filter(r -> r.key().equals(created.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Record not found for parcel " + created.id()));

        EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
        assertThat(envelope.eventType()).isEqualTo("PARCEL_STATUS_CHANGED");
        assertThat(envelope.sequence()).isEqualTo(2L);
        assertThat(envelope.fromStatus()).isEqualTo("CREATED");
        assertThat(envelope.toStatus()).isEqualTo("CANCELLED");
        assertThat(envelope.reason()).isEqualTo("Duplicate delivery order");
    }

    @Test
    @DisplayName("Should publish parcel.status-changed.v1 with cleared courier on re-queue (FAILED -> CREATED) per ADR 005")
    void shouldPublishStatusChangedOnRequeue() throws Exception {
        String customerId = UUID.randomUUID().toString();
        ParcelResponse created = parcelService.createCustomerParcel(customerId, createSampleRequest());
        String courierId = UUID.randomUUID().toString();
        parcelService.assignCourier(created.id(), courierId, "dispatcher-01");

        // Advance to OUT_FOR_DELIVERY then FAILED
        parcelService.updateStatus(created.id(), new ParcelStatusUpdateRequest(ParcelStatus.PICKED_UP, ParcelStatus.CREATED, courierId, null, null, null), courierId);
        parcelService.updateStatus(created.id(), new ParcelStatusUpdateRequest(ParcelStatus.IN_TRANSIT, ParcelStatus.PICKED_UP, courierId, null, null, null), courierId);
        parcelService.updateStatus(created.id(), new ParcelStatusUpdateRequest(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.IN_TRANSIT, courierId, null, null, null), courierId);
        parcelService.updateStatus(created.id(), new ParcelStatusUpdateRequest(ParcelStatus.FAILED, ParcelStatus.OUT_FOR_DELIVERY, courierId, "Customer not home", null, null), courierId);

        // Re-queue: FAILED -> CREATED
        parcelService.updateStatus(created.id(), new ParcelStatusUpdateRequest(ParcelStatus.CREATED, ParcelStatus.FAILED, null, null, null, null), "dispatcher-01");

        List<ConsumerRecord<String, String>> records = pollForRecords(TOPIC_STATUS_CHANGED, 5);
        ConsumerRecord<String, String> requeueRecord = records.stream()
                .filter(r -> {
                    try {
                        EventEnvelope env = objectMapper.readValue(r.value(), EventEnvelope.class);
                        return env.parcelId().equals(created.id()) && "CREATED".equals(env.toStatus()) && "FAILED".equals(env.fromStatus());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("Re-queue event not found"));

        EventEnvelope envelope = objectMapper.readValue(requeueRecord.value(), EventEnvelope.class);
        assertThat(envelope.fromStatus()).isEqualTo("FAILED");
        assertThat(envelope.toStatus()).isEqualTo("CREATED");
        assertThat(envelope.courierId()).isNull(); // cleared per ADR 005
        assertThat(envelope.previousCourierId()).isEqualTo(courierId);
    }

    @Test
    @DisplayName("Should not publish Kafka events when transaction is rolled back (prevent phantom events)")
    void shouldNotPublishEventOnTransactionRollback() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.execute(status -> {
            parcelService.createCustomerParcel("rollback-customer", createSampleRequest());
            throw new RuntimeException("Simulated database failure to trigger rollback");
        })).isInstanceOf(RuntimeException.class);

        // Polling should yield 0 records for rollback-customer
        List<ConsumerRecord<String, String>> records = pollForRecords(TOPIC_CREATED, 1);
        boolean phantomFound = records.stream().anyMatch(r -> {
            try {
                EventEnvelope env = objectMapper.readValue(r.value(), EventEnvelope.class);
                return "rollback-customer".equals(env.customerId());
            } catch (Exception e) {
                return false;
            }
        });
        assertThat(phantomFound).isFalse();
    }
}
