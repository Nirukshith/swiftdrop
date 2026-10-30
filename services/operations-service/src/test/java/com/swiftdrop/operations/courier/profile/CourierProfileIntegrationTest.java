package com.swiftdrop.operations.courier.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.operations.dispatch.ProcessedEventRepository;
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
class CourierProfileIntegrationTest {
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
}
