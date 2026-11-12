package com.swiftdrop.operations.courier.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.operations.dispatch.ProcessedEventRepository;
import com.swiftdrop.operations.OperationsKafkaIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class CourierProfileIntegrationTest extends OperationsKafkaIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CourierProfileService service;
    @Autowired CourierProfileRepository profiles;
    @Autowired ProcessedEventRepository processedEvents;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        profiles.deleteAll();
        processedEvents.deleteAll();
    }

    @Test
    void courierRegistrationIsIdempotentAndDirectoryFiltersAvailability() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.createObjectNode()
            .put("eventId", eventId)
            .put("eventType", "USER_REGISTERED")
            .put("occurredAt", "2026-10-28T10:00:00Z")
            .put("userId", "courier-1")
            .put("email", "courier@example.com")
            .put("fullName", "Saman Perera")
            .put("phoneNumber", "+94771234567")
            .put("role", "COURIER")
            .put("enabled", true)
            .toString();

        service.onUserRegistered(payload);
        service.onUserRegistered(payload);

        assertThat(profiles.count()).isEqualTo(1);
        assertThat(processedEvents.existsByEventId(eventId)).isTrue();
        assertThat(service.listCouriersByZone(null, true)).extracting(CourierSummary::courierId)
            .containsExactly("courier-1");

        service.updateAvailability("courier-1", false);
        assertThat(service.listCouriersByZone(null, true)).isEmpty();
        assertThat(service.isCourierAvailable("courier-1")).isFalse();
    }

    @Test
    void nonCourierRegistrationIsRecordedButDoesNotCreateAProfile() {
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.createObjectNode()
            .put("eventId", eventId)
            .put("eventType", "USER_REGISTERED")
            .put("occurredAt", "2026-10-28T10:00:00Z")
            .put("userId", "customer-1")
            .put("email", "customer@example.com")
            .put("fullName", "Customer One")
            .put("role", "CUSTOMER")
            .put("enabled", true)
            .toString();

        service.onUserRegistered(payload);

        assertThat(profiles.findById("customer-1")).isEmpty();
        assertThat(processedEvents.existsByEventId(eventId)).isTrue();
    }

    @Test
    void invalidRegistrationIsRejectedAndDoesNotPersistAnIdempotencyRecord() {
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.createObjectNode()
            .put("eventId", eventId)
            .put("eventType", "USER_REGISTERED")
            .put("occurredAt", "2026-10-28T10:00:00Z")
            .put("userId", "courier-invalid")
            .put("email", "not-an-email")
            .put("fullName", "Invalid Courier")
            .put("role", "COURIER")
            .toString();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.onUserRegistered(payload))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to process user.registered.v1");

        assertThat(profiles.findById("courier-invalid")).isEmpty();
        assertThat(processedEvents.existsByEventId(eventId)).isFalse();
    }

    @Test
    void laterCourierRegistrationReconcilesIdentityWithoutOverwritingAvailability() {
        String firstEventId = UUID.randomUUID().toString();
        String secondEventId = UUID.randomUUID().toString();
        service.onUserRegistered(registrationPayload(firstEventId, "Saman Perera", "+94770000000"));
        service.updateAvailability("courier-reconcile", false);

        service.onUserRegistered(registrationPayload(secondEventId, "Saman Updated", "+94771111111"));

        CourierProfileEntity profile = profiles.findById("courier-reconcile").orElseThrow();
        assertThat(profile.getName()).isEqualTo("Saman Updated");
        assertThat(profile.getPhone()).isEqualTo("+94771111111");
        assertThat(profile.isAvailability()).isFalse();
    }

    private String registrationPayload(String eventId, String name, String phone) {
        return objectMapper.createObjectNode()
            .put("eventId", eventId)
            .put("eventType", "USER_REGISTERED")
            .put("occurredAt", "2026-10-28T10:00:00Z")
            .put("userId", "courier-reconcile")
            .put("email", "reconcile@example.com")
            .put("fullName", name)
            .put("phoneNumber", phone)
            .put("role", "COURIER")
            .put("enabled", true)
            .toString();
    }
}
