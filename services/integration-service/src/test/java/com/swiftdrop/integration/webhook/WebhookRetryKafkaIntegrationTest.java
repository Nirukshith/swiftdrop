package com.swiftdrop.integration.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.integration.notification.service.NotificationService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "swiftdrop.webhook.allow-http=true",
        "swiftdrop.webhook.allow-local-addresses=true"
})
class WebhookRetryKafkaIntegrationTest {

    private static final String MAIN_TOPIC = "parcel.status-changed.v1";
    private static final String WEBHOOK_DLT = "parcel.status-changed.v1.webhook.dlt";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockBean
    private WebhookDispatcher dispatcher;

    @MockBean
    private NotificationService notificationService;

    @Test
    void exhaustedWebhookRetriesReachOnlyWebhookDltWithOriginalEnvelopeAndHeaders() throws Exception {
        assertExpectedRetryTopicsExist();
        UUID merchantId = UUID.randomUUID();
        String url = "http://127.0.0.1:8789/hook";
        webhookService.updateSettings(merchantId, new UpdateWebhookSettingsRequest(
                url, true, List.of("PARCEL_STATUS_CHANGED"), true));
        EventEnvelope event = statusEvent(merchantId);
        EventEnvelope followingEvent = new EventEnvelope(UUID.randomUUID().toString(),
                "PARCEL_STATUS_CHANGED", Instant.now(), 3L, event.parcelId(), event.trackingNumber(),
                "IN_TRANSIT", "DELIVERED", "courier-123", null, merchantId.toString(), null, null);
        when(dispatcher.dispatch(eq(url), anyString(), anyString(), any()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(3)).contains(event.eventId())
                        ? failedResult(503) : successfulResult());

        String originalEnvelope = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(MAIN_TOPIC, event.parcelId(), originalEnvelope).get();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(deliveryRepository.findFirstByEventIdAndMerchantIdOrderByCreatedAtAsc(
                        UUID.fromString(event.eventId()), merchantId).map(WebhookDeliveryEntity::getStatus))
                        .contains("PENDING_RETRY"));
        kafkaTemplate.send(MAIN_TOPIC, followingEvent.parcelId(), objectMapper.writeValueAsString(followingEvent)).get();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(deliveryRepository.findFirstByEventIdAndMerchantIdOrderByCreatedAtAsc(
                        UUID.fromString(followingEvent.eventId()), merchantId).map(WebhookDeliveryEntity::getStatus))
                        .contains("SUCCESS"));

        ConsumerRecord<String, String> dlt = awaitRecord(WEBHOOK_DLT, originalEnvelope, Duration.ofSeconds(45));
        assertThat(dlt.value()).isEqualTo(originalEnvelope);
        assertThat(dlt.key()).isEqualTo(event.parcelId());
        awaitDeliveryStatus(event, merchantId, "FAILED");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(processedEventRepository.existsByEventIdAndConsumerGroup(
                        UUID.fromString(event.eventId()), WebhookService.CONSUMER_GROUP))
                        .isTrue());
        WebhookDeliveryEntity delivery = deliveryRepository.findFirstByEventIdAndMerchantIdOrderByCreatedAtAsc(
                UUID.fromString(event.eventId()), merchantId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        assertThat(delivery.getAttempts()).isEqualTo(5);
        assertThat(webhookService.getDeliveryDetail(merchantId, delivery.getDeliveryId()).retryHistory()).hasSize(5);
        assertThat(header(dlt, "x-exception-message")).isEqualTo("Target returned HTTP 503");
        assertThat(header(dlt, "x-delivery-id")).isEqualTo(delivery.getDeliveryId().toString());
        assertThat(header(dlt, "x-merchant-id")).isEqualTo(merchantId.toString());
        assertThat(header(dlt, "x-original-topic")).isEqualTo(MAIN_TOPIC);

        verify(notificationService, after(2000).atLeastOnce()).processStatusChangeEvent(any(EventEnvelope.class));
        assertNoMatchingRecord("parcel.status-changed.v1.dlt", originalEnvelope);

        kafkaTemplate.send(MAIN_TOPIC, event.parcelId(), originalEnvelope).get();
        verify(dispatcher, after(2000).times(5)).dispatch(eq(url), anyString(), anyString(),
                argThat(payload -> ((String) payload).contains(event.eventId())));
    }

    @Test
    void clientErrorGoesToWebhookDltWithoutAutomaticRetry() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String url = "http://127.0.0.1:8790/hook";
        webhookService.updateSettings(merchantId, new UpdateWebhookSettingsRequest(
                url, true, List.of("PARCEL_STATUS_CHANGED"), true));
        when(dispatcher.dispatch(eq(url), anyString(), anyString(), any()))
                .thenReturn(failedResult(400));

        EventEnvelope event = statusEvent(merchantId);
        String originalEnvelope = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(MAIN_TOPIC, event.parcelId(), originalEnvelope).get();

        ConsumerRecord<String, String> dlt = awaitRecord(WEBHOOK_DLT, originalEnvelope, Duration.ofSeconds(15));
        assertThat(header(dlt, "x-original-topic")).isEqualTo(MAIN_TOPIC);
        awaitDeliveryStatus(event, merchantId, "FAILED");
        WebhookDeliveryEntity delivery = deliveryRepository.findFirstByEventIdAndMerchantIdOrderByCreatedAtAsc(
                UUID.fromString(event.eventId()), merchantId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        assertThat(delivery.getAttempts()).isEqualTo(1);
        verify(dispatcher, after(2000).times(1)).dispatch(eq(url), anyString(), anyString(), any());
    }

    private void awaitDeliveryStatus(EventEnvelope event, UUID merchantId, String expectedStatus) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(deliveryRepository.findFirstByEventIdAndMerchantIdOrderByCreatedAtAsc(
                        UUID.fromString(event.eventId()), merchantId).map(WebhookDeliveryEntity::getStatus))
                        .contains(expectedStatus));
    }

    private static EventEnvelope statusEvent(UUID merchantId) {
        return new EventEnvelope(UUID.randomUUID().toString(), "PARCEL_STATUS_CHANGED", Instant.now(),
                2L, UUID.randomUUID().toString(), "SD-20261110-100200", "PICKED_UP", "IN_TRANSIT",
                "courier-123", null, merchantId.toString(), null, null);
    }

    private static WebhookDispatcher.DispatchResult failedResult(int status) {
        return new WebhookDispatcher.DispatchResult(false, status, 10, Map.of(), Map.of(), null,
                null, "Target returned HTTP " + status, null, null);
    }

    private static WebhookDispatcher.DispatchResult successfulResult() {
        return new WebhookDispatcher.DispatchResult(true, 200, 10, Map.of(), Map.of(), "ok",
                "ok", null, null, null);
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        assertThat(record.headers().lastHeader(name)).as(name).isNotNull();
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static ConsumerRecord<String, String> awaitRecord(String topic, String expectedValue,
                                                               Duration timeout) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(timeout);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
                    if (expectedValue.equals(record.value())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("Expected event did not reach " + topic);
    }

    private static void assertNoMatchingRecord(String topic, String value) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            consumer.subscribe(List.of(topic));
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(2))) {
                assertThat(record.value()).isNotEqualTo(value);
            }
        }
    }

    private static Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "webhook-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return properties;
    }

    private static void assertExpectedRetryTopicsExist() {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                Set<String> names = admin.listTopics().names().get();
                assertThat(names).contains(
                        "parcel.status-changed.v1.retry-1000",
                        "parcel.status-changed.v1.retry-2000",
                        "parcel.status-changed.v1.retry-4000",
                        "parcel.status-changed.v1.retry-8000",
                        "parcel.status-changed.v1.dlt",
                        "parcel.status-changed.v1.webhook.retry-1000",
                        "parcel.status-changed.v1.webhook.retry-2000",
                        "parcel.status-changed.v1.webhook.retry-4000",
                        "parcel.status-changed.v1.webhook.retry-8000",
                        WEBHOOK_DLT);
            });
        }
    }
}
