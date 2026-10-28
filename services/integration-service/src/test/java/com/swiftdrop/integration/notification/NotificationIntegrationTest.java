package com.swiftdrop.integration.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.integration.notification.client.AuthContactClient;
import com.swiftdrop.integration.notification.client.ContactResolutionException;
import com.swiftdrop.integration.notification.dto.NotificationPageResponse;
import com.swiftdrop.integration.notification.dto.UserContactDto;
import com.swiftdrop.integration.notification.model.NotificationLogEntity;
import com.swiftdrop.integration.notification.repository.NotificationLogRepository;
import com.swiftdrop.integration.notification.repository.NotificationProcessedEventRepository;
import com.swiftdrop.integration.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class NotificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @Container
    static GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.32.0"))
            .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private NotificationProcessedEventRepository processedEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthContactClient authContactClient;

    private RestClient mailpitClient;

    private static final String TOPIC = "parcel.status-changed.v1";

    @BeforeEach
    void setUp() {
        mailpitClient = RestClient.builder()
                .baseUrl("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025))
                .build();

        // Default mock answer: any customerId except "cust-missing*" resolves contact
        when(authContactClient.getContact(any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            if (id != null && id.startsWith("cust-missing")) {
                return Optional.empty();
            }
            return Optional.of(new UserContactDto(id, "Test User", id + "@example.com", "+94771234567", "CUSTOMER"));
        });

        // Clear Mailpit messages before each test
        try {
            mailpitClient.delete().uri("/api/v1/messages").retrieve().toBodilessEntity();
        } catch (Exception ignored) {}
    }

    private JsonNode getMailpitMessages() {
        return mailpitClient.get()
                .uri("/api/v1/messages")
                .retrieve()
                .body(JsonNode.class);
    }

    @Test
    @DisplayName("Done when: A parcel status change event produces one stored notification and one email in Mailpit")
    void statusChangeEvent_producesOneStoredNotification_andOneEmailInMailpit() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String customerId = "cust-" + UUID.randomUUID();
        String parcelId = "pcl-" + UUID.randomUUID();
        String trackingNo = "SD-20261028-999";
        String customerEmail = customerId + "@example.com";

        EventEnvelope event = new EventEnvelope(
                eventId,
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                5L,
                parcelId,
                trackingNo,
                "IN_TRANSIT",
                "OUT_FOR_DELIVERY",
                "courier-1",
                null,
                null,
                customerId,
                null
        );

        kafkaTemplate.send(TOPIC, parcelId, objectMapper.writeValueAsString(event)).get();

        // 1. Verify stored IN_APP notification
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<NotificationLogEntity> notifications = notificationLogRepository.findAll();
            assertThat(notifications).anyMatch(n ->
                    eventId.equals(n.getEventId()) && "IN_APP".equals(n.getChannel()) && customerId.equals(n.getUserId())
            );
        });

        // 2. Verify bell query API returns notification
        NotificationPageResponse page = notificationService.getNotifications(customerId, 0, 10, false);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.unreadCount()).isEqualTo(1);
        assertThat(page.items().get(0).title()).isEqualTo("SwiftDrop: Out for Delivery Today!");
        assertThat(page.items().get(0).read()).isFalse();

        // 3. Verify exactly one email delivered to Mailpit
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode messages = getMailpitMessages();
            assertThat(messages.get("total").asInt()).isEqualTo(1);
            JsonNode firstMsg = messages.get("messages").get(0);
            assertThat(firstMsg.get("Subject").asText()).isEqualTo("SwiftDrop: Out for Delivery Today!");
            assertThat(firstMsg.get("To").get(0).get("Address").asText()).isEqualTo(customerEmail);
        });

        // 4. Verify mark as read updates unread count
        String notifId = page.items().get(0).id();
        notificationService.markAsRead(notifId, customerId);

        NotificationPageResponse updatedPage = notificationService.getNotifications(customerId, 0, 10, false);
        assertThat(updatedPage.unreadCount()).isEqualTo(0);
        assertThat(updatedPage.items().get(0).read()).isTrue();
    }

    @Test
    @DisplayName("Duplicate event sends only one email and creates only one stored notification")
    void duplicateEvent_isIdempotent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String customerId = "cust-idempotent-" + UUID.randomUUID();
        String parcelId = "pcl-" + UUID.randomUUID();
        String trackingNo = "SD-20261028-IDEMP";
        String customerEmail = customerId + "@example.com";

        EventEnvelope event = new EventEnvelope(
                eventId,
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                3L,
                parcelId,
                trackingNo,
                "CREATED",
                "PICKED_UP",
                "courier-1",
                null,
                null,
                customerId,
                null
        );

        String json = objectMapper.writeValueAsString(event);

        // Send first time
        kafkaTemplate.send(TOPIC, parcelId, json).get();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode messages = getMailpitMessages();
            assertThat(messages.get("total").asInt()).isEqualTo(1);
        });

        // Send duplicate event with same eventId
        kafkaTemplate.send(TOPIC, parcelId, json).get();

        // Wait a bit to ensure consumer processed it and verify no extra email sent
        Thread.sleep(2000);
        JsonNode messagesAfterDuplicate = getMailpitMessages();
        assertThat(messagesAfterDuplicate.get("total").asInt()).isEqualTo(1);

        // Verify only 1 IN_APP and 1 EMAIL notification in DB
        List<NotificationLogEntity> inAppRows = notificationLogRepository.findAll().stream()
                .filter(n -> eventId.equals(n.getEventId()) && "IN_APP".equals(n.getChannel()))
                .toList();
        assertThat(inAppRows).hasSize(1);
    }

    @Test
    @DisplayName("Stale event with sequence <= highest processed sequence is ignored")
    void staleEvent_isIgnored() throws Exception {
        String parcelId = "pcl-seq-" + UUID.randomUUID();
        String customerId = "cust-seq-" + UUID.randomUUID();
        String customerEmail = customerId + "@example.com";

        // 1. Send modern event with sequence = 5
        String event5Id = UUID.randomUUID().toString();
        EventEnvelope event5 = new EventEnvelope(
                event5Id,
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                5L,
                parcelId,
                "SD-20261028-SEQ",
                "IN_TRANSIT",
                "OUT_FOR_DELIVERY",
                "courier-1",
                null,
                null,
                customerId,
                null
        );
        kafkaTemplate.send(TOPIC, parcelId, objectMapper.writeValueAsString(event5)).get();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(getMailpitMessages().get("total").asInt()).isEqualTo(1);
        });

        // 2. Send out-of-order stale event with sequence = 3 (e.g. delayed PICKED_UP)
        String staleEventId = UUID.randomUUID().toString();
        EventEnvelope staleEvent = new EventEnvelope(
                staleEventId,
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                3L,
                parcelId,
                "SD-20261028-SEQ",
                "CREATED",
                "PICKED_UP",
                "courier-1",
                null,
                null,
                customerId,
                null
        );
        kafkaTemplate.send(TOPIC, parcelId, objectMapper.writeValueAsString(staleEvent)).get();

        // Wait to give consumer time to inspect
        Thread.sleep(2000);

        // Mailpit must STILL have only 1 email
        JsonNode messages = getMailpitMessages();
        assertThat(messages.get("total").asInt()).isEqualTo(1);

        // No notification log row for staleEventId
        List<NotificationLogEntity> staleRows = notificationLogRepository.findAll().stream()
                .filter(n -> staleEventId.equals(n.getEventId()))
                .toList();
        assertThat(staleRows).isEmpty();
    }

    @Test
    @DisplayName("Contact lookup failure path: Throws ContactResolutionException when contact cannot be resolved")
    void contactLookupFailurePath_throwsException() {
        String customerId = "cust-missing-" + UUID.randomUUID();
        String parcelId = "pcl-" + UUID.randomUUID();

        when(authContactClient.getContact(customerId))
                .thenReturn(Optional.empty());

        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                1L,
                parcelId,
                "SD-20261028-FAIL",
                "CREATED",
                "PICKED_UP",
                "courier-1",
                null,
                null,
                customerId,
                null
        );

        assertThatThrownBy(() -> notificationService.processStatusChangeEvent(event))
                .isInstanceOf(ContactResolutionException.class)
                .hasMessageContaining("Could not resolve contact for customer " + customerId);
    }
}

