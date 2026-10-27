package com.swiftdrop.integration.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    public static final String CONSUMER_GROUP = "integration-webhook";

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final WebhookCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final WebhookDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public WebhookService(
            WebhookEndpointRepository endpointRepository,
            WebhookDeliveryRepository deliveryRepository,
            ProcessedEventRepository processedEventRepository,
            WebhookCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            WebhookDispatcher dispatcher,
            ObjectMapper objectMapper) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.processedEventRepository = processedEventRepository;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WebhookSettingsResponse getSettings(UUID merchantId) {
        return endpointRepository.findById(merchantId)
                .map(endpoint -> new WebhookSettingsResponse(
                        endpoint.getMerchantId(),
                        endpoint.getUrl(),
                        endpoint.isActive(),
                        endpoint.getSubscribedEventsList(),
                        endpoint.getEncryptedSecret() != null,
                        endpoint.getSecretPreview(),
                        null,
                        endpoint.getCreatedAt(),
                        endpoint.getUpdatedAt()
                ))
                .orElseGet(() -> new WebhookSettingsResponse(
                        merchantId,
                        null,
                        false,
                        List.of("PARCEL_CREATED", "PARCEL_STATUS_CHANGED"),
                        false,
                        null,
                        null,
                        Instant.now(),
                        Instant.now()
                ));
    }

    @Transactional
    public WebhookSettingsResponse updateSettings(UUID merchantId, UpdateWebhookSettingsRequest request) {
        if (request.url() != null && !request.url().isBlank()) {
            ssrfGuard.validateUrl(request.url());
        }

        Optional<WebhookEndpointEntity> existingOpt = endpointRepository.findById(merchantId);
        WebhookEndpointEntity endpoint;
        String rawSecretToReturn = null;

        if (existingOpt.isPresent()) {
            endpoint = existingOpt.get();
            endpoint.setUrl(request.url());
            endpoint.setActive(Boolean.TRUE.equals(request.active()));
            if (request.subscribedEvents() != null) {
                endpoint.setSubscribedEventsList(request.subscribedEvents());
            }
            if (request.isRegenerateSecret() || endpoint.getEncryptedSecret() == null) {
                String plainSecret = cryptoService.generateSecret();
                endpoint.setEncryptedSecret(cryptoService.encrypt(plainSecret));
                endpoint.setSecretPreview(cryptoService.generatePreview(plainSecret));
                rawSecretToReturn = plainSecret;
            }
            endpoint.setUpdatedAt(Instant.now());
        } else {
            String plainSecret = cryptoService.generateSecret();
            String encrypted = cryptoService.encrypt(plainSecret);
            String preview = cryptoService.generatePreview(plainSecret);
            rawSecretToReturn = plainSecret;

            endpoint = new WebhookEndpointEntity(
                    merchantId,
                    request.url(),
                    Boolean.TRUE.equals(request.active()),
                    request.subscribedEvents() != null ? String.join(",", request.subscribedEvents()) : "PARCEL_CREATED,PARCEL_STATUS_CHANGED",
                    encrypted,
                    preview
            );
        }

        endpointRepository.save(endpoint);

        return new WebhookSettingsResponse(
                endpoint.getMerchantId(),
                endpoint.getUrl(),
                endpoint.isActive(),
                endpoint.getSubscribedEventsList(),
                true,
                endpoint.getSecretPreview(),
                rawSecretToReturn,
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }

    public TestWebhookResultResponse triggerTestWebhook(UUID merchantId, TestWebhookRequest request) {
        WebhookEndpointEntity endpoint = endpointRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook settings not configured for merchant"));

        String targetUrl = (request != null && request.targetUrlOverride() != null && !request.targetUrlOverride().isBlank())
                ? request.targetUrlOverride().trim()
                : endpoint.getUrl();

        if (targetUrl == null || targetUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No webhook destination URL specified or configured");
        }

        ssrfGuard.validateUrl(targetUrl);

        String secret = cryptoService.decrypt(endpoint.getEncryptedSecret());
        UUID testDeliveryId = UUID.randomUUID();
        UUID testEventId = UUID.randomUUID();
        String eventType = (request != null && request.sampleEventType() != null) ? request.sampleEventType() : "TEST_EVENT";

        WebhookPayload testPayload = new WebhookPayload(
                testDeliveryId,
                testEventId,
                eventType,
                Instant.now(),
                1L,
                merchantId,
                new WebhookParcelFields(
                        "pcl-test-" + UUID.randomUUID().toString().substring(0, 8),
                        "SD-TEST-" + Instant.now().getEpochSecond(),
                        "OUT_FOR_DELIVERY",
                        "IN_TRANSIT",
                        "OUT_FOR_DELIVERY",
                        1.5,
                        "Test Recipient",
                        "123 Test Street, Colombo",
                        Instant.now(),
                        null
                )
        );

        WebhookDispatcher.DispatchResult result = dispatcher.dispatch(targetUrl, secret, testDeliveryId.toString(), testPayload);

        return new TestWebhookResultResponse(
                result.success(),
                result.statusCode(),
                result.latencyMs(),
                Instant.now(),
                targetUrl,
                result.signatureSent(),
                result.timestampSent(),
                result.responseBodySummary(),
                result.errorMessage()
        );
    }

    @Transactional(readOnly = true)
    public WebhookDeliveryLogPage listDeliveries(UUID merchantId, String status, String eventType,
                                                Instant from, Instant to, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WebhookDeliveryEntity> entityPage = deliveryRepository.findDeliveries(
                merchantId, status, eventType, from, to, pageable
        );

        List<WebhookDeliveryLogItem> items = entityPage.getContent().stream()
                .map(e -> new WebhookDeliveryLogItem(
                        e.getDeliveryId(),
                        e.getEventId(),
                        e.getEventType(),
                        e.getTrackingNumber(),
                        e.getTargetUrl(),
                        e.getStatus(),
                        e.getHttpStatusCode(),
                        e.getAttempts(),
                        e.getLastAttemptAt(),
                        e.getNextRetryAt(),
                        e.getErrorSummary(),
                        e.getCreatedAt()
                ))
                .toList();

        return new WebhookDeliveryLogPage(
                items,
                entityPage.getTotalElements(),
                entityPage.getTotalPages(),
                entityPage.getNumber(),
                entityPage.getSize()
        );
    }

    @Transactional(readOnly = true)
    public WebhookDeliveryDetailResponse getDeliveryDetail(UUID merchantId, UUID deliveryId) {
        WebhookDeliveryEntity entity = deliveryRepository.findByDeliveryIdAndMerchantId(deliveryId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook delivery not found"));

        return toDeliveryDetailResponse(entity);
    }

    @Transactional
    public WebhookDeliveryDetailResponse retryDelivery(UUID merchantId, UUID deliveryId) {
        WebhookDeliveryEntity delivery = deliveryRepository.findByDeliveryIdAndMerchantId(deliveryId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook delivery not found"));

        WebhookEndpointEntity endpoint = endpointRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook settings not configured"));

        String secret = cryptoService.decrypt(endpoint.getEncryptedSecret());

        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastAttemptAt(Instant.now());

        WebhookDispatcher.DispatchResult result = dispatcher.dispatch(
                delivery.getTargetUrl(), secret, delivery.getDeliveryId().toString(), delivery.getPayload()
        );

        delivery.setHttpStatusCode(result.statusCode());
        delivery.setStatus(result.success() ? "SUCCESS" : "FAILED");
        delivery.setErrorSummary(result.errorMessage());
        try {
            delivery.setRequestHeaders(objectMapper.writeValueAsString(result.requestHeaders()));
            delivery.setResponseHeaders(objectMapper.writeValueAsString(result.responseHeaders()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize headers: {}", e.getMessage());
        }
        delivery.setResponseBody(result.responseBodySummary());

        WebhookDeliveryAttemptEntity attempt = new WebhookDeliveryAttemptEntity(
                delivery,
                delivery.getAttempts(),
                Instant.now(),
                result.statusCode(),
                result.latencyMs(),
                result.success(),
                result.errorMessage()
        );
        delivery.addAttempt(attempt);

        deliveryRepository.save(delivery);

        return toDeliveryDetailResponse(delivery);
    }

    @Transactional
    public void processParcelStatusChangedEvent(EventEnvelope event) {
        if (event == null || event.eventId() == null) {
            return;
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(event.eventId());
        } catch (IllegalArgumentException e) {
            log.warn("Malformed eventId in parcel status changed event: {}", event.eventId());
            return;
        }

        // Idempotency check: consumer-scoped
        if (processedEventRepository.existsByEventIdAndConsumerGroup(eventId, CONSUMER_GROUP)) {
            log.info("Event {} has already been processed by consumer group {}; skipping.", eventId, CONSUMER_GROUP);
            return;
        }

        // Only process merchant-associated parcels
        if (event.merchantId() == null || event.merchantId().isBlank()) {
            log.debug("Event {} does not carry a merchantId; skipping webhook dispatch.", eventId);
            recordProcessedEvent(eventId);
            return;
        }

        UUID merchantId;
        try {
            merchantId = UUID.fromString(event.merchantId());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid merchantId UUID in event {}: {}", eventId, event.merchantId());
            recordProcessedEvent(eventId);
            return;
        }

        Optional<WebhookEndpointEntity> endpointOpt = endpointRepository.findById(merchantId);
        if (endpointOpt.isEmpty() || !endpointOpt.get().isActive() || endpointOpt.get().getUrl() == null) {
            log.debug("No active webhook endpoint configured for merchant {}; skipping event {}.", merchantId, eventId);
            recordProcessedEvent(eventId);
            return;
        }

        WebhookEndpointEntity endpoint = endpointOpt.get();
        if (!endpoint.isSubscribedTo(event.eventType())) {
            log.debug("Merchant {} is not subscribed to event type {}; skipping event {}.", merchantId, event.eventType(), eventId);
            recordProcessedEvent(eventId);
            return;
        }

        // Create webhook delivery record in PENDING state
        UUID deliveryId = UUID.randomUUID();
        WebhookPayload payload = new WebhookPayload(
                deliveryId,
                eventId,
                event.eventType(),
                event.occurredAt() != null ? event.occurredAt() : Instant.now(),
                event.sequence() != null ? event.sequence() : 1L,
                merchantId,
                new WebhookParcelFields(
                        event.parcelId(),
                        event.trackingNumber(),
                        event.toStatus(),
                        event.fromStatus(),
                        event.toStatus(),
                        null,
                        null,
                        null,
                        event.occurredAt() != null ? event.occurredAt() : Instant.now(),
                        event.reason()
                )
        );

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize webhook payload: {}", e.getMessage(), e);
            recordProcessedEvent(eventId);
            return;
        }

        WebhookDeliveryEntity delivery = new WebhookDeliveryEntity();
        delivery.setDeliveryId(deliveryId);
        delivery.setEventId(eventId);
        delivery.setMerchantId(merchantId);
        delivery.setEventType(event.eventType());
        delivery.setTrackingNumber(event.trackingNumber());
        delivery.setTargetUrl(endpoint.getUrl());
        delivery.setStatus("PENDING");
        delivery.setAttempts(1);
        delivery.setPayload(payloadJson);
        delivery.setCreatedAt(Instant.now());
        delivery.setLastAttemptAt(Instant.now());

        String secret = cryptoService.decrypt(endpoint.getEncryptedSecret());
        WebhookDispatcher.DispatchResult result = dispatcher.dispatch(endpoint.getUrl(), secret, deliveryId.toString(), payloadJson);

        delivery.setHttpStatusCode(result.statusCode());
        delivery.setStatus(result.success() ? "SUCCESS" : "FAILED");
        delivery.setErrorSummary(result.errorMessage());
        try {
            delivery.setRequestHeaders(objectMapper.writeValueAsString(result.requestHeaders()));
            delivery.setResponseHeaders(objectMapper.writeValueAsString(result.responseHeaders()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize headers: {}", e.getMessage());
        }
        delivery.setResponseBody(result.responseBodySummary());

        WebhookDeliveryAttemptEntity attempt = new WebhookDeliveryAttemptEntity(
                delivery,
                1,
                Instant.now(),
                result.statusCode(),
                result.latencyMs(),
                result.success(),
                result.errorMessage()
        );
        delivery.addAttempt(attempt);

        deliveryRepository.save(delivery);
        recordProcessedEvent(eventId);
    }

    private void recordProcessedEvent(UUID eventId) {
        processedEventRepository.save(new ProcessedEventEntity(eventId, CONSUMER_GROUP, Instant.now()));
    }

    private WebhookDeliveryDetailResponse toDeliveryDetailResponse(WebhookDeliveryEntity entity) {
        WebhookPayload payload = null;
        if (entity.getPayload() != null) {
            try {
                payload = objectMapper.readValue(entity.getPayload(), WebhookPayload.class);
            } catch (JsonProcessingException e) {
                log.warn("Could not deserialize saved webhook payload: {}", e.getMessage());
            }
        }

        Map<String, String> requestHeaders = Collections.emptyMap();
        if (entity.getRequestHeaders() != null) {
            try {
                requestHeaders = objectMapper.readValue(entity.getRequestHeaders(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                // Ignore parse error
            }
        }

        Map<String, String> responseHeaders = Collections.emptyMap();
        if (entity.getResponseHeaders() != null) {
            try {
                responseHeaders = objectMapper.readValue(entity.getResponseHeaders(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                // Ignore parse error
            }
        }

        List<DeliveryAttemptRecord> history = new ArrayList<>();
        if (entity.getRetryHistory() != null) {
            for (WebhookDeliveryAttemptEntity attempt : entity.getRetryHistory()) {
                history.add(new DeliveryAttemptRecord(
                        attempt.getAttemptNumber(),
                        attempt.getAttemptedAt(),
                        attempt.getHttpStatusCode(),
                        attempt.getLatencyMs(),
                        attempt.isSuccess(),
                        attempt.getErrorMessage()
                ));
            }
        }

        return new WebhookDeliveryDetailResponse(
                entity.getDeliveryId(),
                entity.getEventId(),
                entity.getEventType(),
                entity.getStatus(),
                entity.getTargetUrl(),
                entity.getAttempts(),
                entity.getHttpStatusCode(),
                requestHeaders,
                payload,
                responseHeaders,
                entity.getResponseBody(),
                entity.getLastAttemptAt(),
                entity.getNextRetryAt(),
                history,
                entity.getCreatedAt()
        );
    }
}
