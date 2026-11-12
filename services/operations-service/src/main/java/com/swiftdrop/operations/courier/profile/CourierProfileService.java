package com.swiftdrop.operations.courier.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.operations.dispatch.ProcessedEventEntity;
import com.swiftdrop.operations.dispatch.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CourierProfileService implements CourierDirectory {
    private static final Logger log = LoggerFactory.getLogger(CourierProfileService.class);
    private static final Set<String> USER_ROLES = Set.of("CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN");

    private final CourierProfileRepository repository;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    public CourierProfileService(
        CourierProfileRepository repository,
        ProcessedEventRepository processedEvents,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 16000),
        autoCreateTopics = "true",
        retryTopicSuffix = ".retry",
        dltTopicSuffix = ".dlt"
    )
    @KafkaListener(topics = "${swiftdrop.kafka.topics.user-registered:user.registered.v1}", groupId = "operations-courier-profile")
    @Transactional
    public void onUserRegistered(String payload) {
        try {
            UserRegisteredEvent event = objectMapper.readValue(payload, UserRegisteredEvent.class);
            if (event.eventId() == null || event.eventId().isBlank() || processedEvents.existsByEventId(event.eventId())) {
                return;
            }
            validateEvent(event);
            if ("COURIER".equalsIgnoreCase(event.role()) && event.userId() != null && !event.userId().isBlank()) {
                CourierProfileEntity profile = repository.findById(event.userId()).orElseGet(() -> CourierProfileEntity.builder()
                    .userId(event.userId())
                    .availability(true)
                    .build());
                profile.setName(event.fullName() == null ? "" : event.fullName());
                profile.setPhone(event.phoneNumber());
                repository.save(profile);
            }
            processedEvents.save(new ProcessedEventEntity(event.eventId(), "user.registered.v1", event.userId()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to process user.registered.v1", exception);
        }
    }

    @DltHandler
    public void handleDlt(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Exhausted retry attempts for courier profile event from topic {}: {}", topic, payload);
    }

    private void validateEvent(UserRegisteredEvent event) {
        try {
            UUID.fromString(event.eventId());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("eventId must be a UUID", exception);
        }
        if (!"USER_REGISTERED".equals(event.eventType())) {
            throw new IllegalArgumentException("eventType must be USER_REGISTERED");
        }
        if (event.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (event.userId() == null || event.userId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (event.email() == null || event.email().isBlank() || !event.email().contains("@")) {
            throw new IllegalArgumentException("email is required");
        }
        if (event.fullName() == null || event.fullName().isBlank()) {
            throw new IllegalArgumentException("fullName is required");
        }
        if (event.role() == null || !USER_ROLES.contains(event.role().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("role is invalid");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourierSummary> findCourierById(String courierId) {
        return repository.findById(courierId).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourierSummary> listCouriersByZone(String zone, boolean availableOnly) {
        List<CourierProfileEntity> profiles = availableOnly
            ? (zone == null || zone.isBlank() ? repository.findByAvailabilityTrue() : repository.findByZoneIgnoreCaseAndAvailabilityTrue(zone))
            : (zone == null || zone.isBlank() ? repository.findAll() : repository.findByZoneIgnoreCase(zone));
        return profiles.stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourierSummary> listAllCouriers() {
        return repository.findAll().stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCourierAvailable(String courierId) {
        return findCourierById(courierId).map(CourierSummary::available).orElse(false);
    }

    @Override
    @Transactional
    public void updateAvailability(String courierId, boolean available) {
        repository.findById(courierId).ifPresent(profile -> {
            profile.setAvailability(available);
            repository.save(profile);
        });
    }

    private CourierSummary toSummary(CourierProfileEntity profile) {
        return new CourierSummary(profile.getUserId(), profile.getName(), profile.getPhone(), profile.getZone(), profile.isAvailability(), profile.getVehicleType());
    }
}
