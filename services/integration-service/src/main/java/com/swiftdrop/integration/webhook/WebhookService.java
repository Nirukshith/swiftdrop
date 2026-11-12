package com.swiftdrop.integration.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.integration.merchant.MerchantOrder;
import com.swiftdrop.integration.merchant.MerchantOrderQuery;
import com.swiftdrop.integration.merchant.ParcelStatus;
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

    public enum DeliveryDisposition {
        SKIPPED, SUCCESS, RETRYABLE_FAILURE, TERMINAL_FAILURE
    }

    public record DeliveryOutcome(DeliveryDisposition disposition, UUID deliveryId,
                                  UUID merchantId, String errorMessage) {
        static DeliveryOutcome skipped() {
            return new DeliveryOutcome(DeliveryDisposition.SKIPPED, null, null, null);
        }
    }

    private final WebhookEndpointRepository endpointRepository;
    private final MerchantOrderQuery merchantOrderQuery;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final WebhookCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final WebhookDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public WebhookService(
            WebhookEndpointRepository endpointRepository,
            MerchantOrderQuery merchantOrderQuery,
            WebhookDeliveryRepository deliveryRepository,
            ProcessedEventRepository processedEventRepository,
            WebhookCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            WebhookDispatcher dispatcher,
            ObjectMapper objectMapper) {
        this.endpointRepository = endpointRepository;
        this.merchantOrderQuery = merchantOrderQuery;
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
        ssrfGuard.validateUrl(request.url());

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
                        safeFailureSummary(e.getErrorSummary()),
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
        WebhookDeliveryEntity delivery = deliveryRepository.findForRetry(deliveryId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook delivery not found"));

        if (!"FAILED".equals(delivery.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only failed webhook deliveries can be retried");
        }

        WebhookEndpointEntity endpoint = endpointRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook settings not configured"));

        if (!endpoint.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook endpoint is inactive");
        }

        if (endpoint.getUrl() == null || endpoint.getEncryptedSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook endpoint is incomplete");
        }
        String secret = cryptoService.decrypt(endpoint.getEncryptedSecret());

        // The scoped row lock serializes concurrent retries through the final status check.
        // Manual retry executes immediately as allowed by the merged OpenAPI contract.
        // It retains the attempt history and uses the currently configured destination.
        delivery.setTargetUrl(endpoint.getUrl());
        delivery.setAttempts(1);
        delivery.setNextRetryAt(null);
        Instant attemptedAt = Instant.now();
        delivery.setLastAttemptAt(attemptedAt);

        WebhookDispatcher.DispatchResult result;
        try {
            result = dispatcher.dispatch(delivery.getTargetUrl(), secret,
                    delivery.getDeliveryId().toString(), delivery.getPayload());
        } catch (SsrfException e) {
            boolean transientDnsFailure = e.isTransientDnsFailure();
            result = new WebhookDispatcher.DispatchResult(false,
                    transientDnsFailure ? 0 : 400, 0, Map.of(), Map.of(), null, null,
                    transientDnsFailure ? "Webhook DNS resolution failed" : "Webhook destination rejected",
                    null, null);
        }

        delivery.setHttpStatusCode(result.statusCode());
        delivery.setStatus(result.success() ? "SUCCESS" : "FAILED");
        delivery.setErrorSummary(result.errorMessage());
        try {
            delivery.setRequestHeaders(objectMapper.writeValueAsString(result.requestHeaders()));
            delivery.setResponseHeaders(objectMapper.writeValueAsString(result.responseHeaders()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize webhook headers");
        }
        // Merchant endpoints may return credentials or PII. Keep transport diagnostics,
        // but do not persist an untrusted response body in delivery logs.
        delivery.setResponseBody(null);

        WebhookDeliveryAttemptEntity attempt = new WebhookDeliveryAttemptEntity(
                delivery,
                delivery.getAttempts(),
                attemptedAt,
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
    public DeliveryOutcome processParcelStatusChangedEvent(EventEnvelope event) {
        return processParcelStatusChangedEvent(event, false);
    }

    @Transactional
    public DeliveryOutcome processParcelStatusChangedEvent(EventEnvelope event, boolean fromRetryTopic) {
        if (event == null || event.eventId() == null) {
            return DeliveryOutcome.skipped();
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(event.eventId());
        } catch (IllegalArgumentException e) {
            log.warn("Malformed eventId in parcel status changed event");
            return DeliveryOutcome.skipped();
        }

        if (processedEventRepository.existsByEventIdAndConsumerGroup(eventId, CONSUMER_GROUP)) {
            return DeliveryOutcome.skipped();
        }
        if (event.merchantId() == null || event.merchantId().isBlank()) {
            recordProcessedEvent(eventId);
            return DeliveryOutcome.skipped();
        }

        UUID merchantId;
        try {
            merchantId = UUID.fromString(event.merchantId());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid merchantId UUID in event {}", eventId);
            recordProcessedEvent(eventId);
            return DeliveryOutcome.skipped();
        }

        if (!projectMerchantOrderStatus(event, merchantId)) {
            recordProcessedEvent(eventId);
            return DeliveryOutcome.skipped();
        }

        Optional<WebhookDeliveryEntity> existing = deliveryRepository
                .findFirstByEventIdAndMerchantIdOrderByCreatedAtAsc(eventId, merchantId);
        if (existing.isPresent() && ("SUCCESS".equals(existing.get().getStatus())
                || "FAILED".equals(existing.get().getStatus())
                || "CANCELLED".equals(existing.get().getStatus()))) {
            recordProcessedEvent(eventId);
            return DeliveryOutcome.skipped();
        }
        if (existing.isPresent() && !fromRetryTopic) {
            // A duplicate on the main topic must not bypass the Kafka retry delay.
            return DeliveryOutcome.skipped();
        }

        Optional<WebhookEndpointEntity> endpointOpt = endpointRepository.findById(merchantId);
        if (endpointOpt.isEmpty() || !endpointOpt.get().isActive() || endpointOpt.get().getUrl() == null
                || !endpointOpt.get().isSubscribedTo(event.eventType())) {
            existing.ifPresent(delivery -> {
                delivery.setStatus("CANCELLED");
                delivery.setNextRetryAt(null);
                deliveryRepository.save(delivery);
            });
            recordProcessedEvent(eventId);
            return DeliveryOutcome.skipped();
        }

        WebhookEndpointEntity endpoint = endpointOpt.get();
        WebhookDeliveryEntity delivery = existing.orElseGet(() -> newDelivery(event, eventId, merchantId, endpoint));
        int attemptNumber = delivery.getAttempts() + 1;
        Instant attemptedAt = Instant.now();
        delivery.setAttempts(attemptNumber);
        delivery.setStatus("PENDING");
        delivery.setLastAttemptAt(attemptedAt);
        delivery.setNextRetryAt(null);

        WebhookDispatcher.DispatchResult result;
        try {
            String secret = cryptoService.decrypt(endpoint.getEncryptedSecret());
            result = dispatcher.dispatch(delivery.getTargetUrl(), secret,
                    delivery.getDeliveryId().toString(), delivery.getPayload());
        } catch (SsrfException e) {
            boolean retryableDnsFailure = e.isTransientDnsFailure();
            result = new WebhookDispatcher.DispatchResult(false, retryableDnsFailure ? 0 : 400, 0,
                    Map.of(), Map.of(), null, null,
                    retryableDnsFailure ? "Webhook DNS resolution failed" : "Webhook destination rejected",
                    null, null);
        }

        delivery.setHttpStatusCode(result.statusCode());
        delivery.setErrorSummary(result.errorMessage());
        try {
            delivery.setRequestHeaders(objectMapper.writeValueAsString(result.requestHeaders()));
            delivery.setResponseHeaders(objectMapper.writeValueAsString(result.responseHeaders()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize webhook headers");
        }
        delivery.setResponseBody(null);
        delivery.addAttempt(new WebhookDeliveryAttemptEntity(delivery, attemptNumber, attemptedAt,
                result.statusCode(), result.latencyMs(), result.success(), result.errorMessage()));

        DeliveryDisposition disposition;
        if (result.success()) {
            delivery.setStatus("SUCCESS");
            disposition = DeliveryDisposition.SUCCESS;
        } else if (WebhookDeliveryPolicy.isRetryable(result)) {
            delivery.setNextRetryAt(WebhookDeliveryPolicy.nextDelay(attemptNumber)
                    .map(delay -> Instant.now().plus(delay)).orElse(null));
            delivery.setStatus(attemptNumber < WebhookDeliveryPolicy.MAX_ATTEMPTS ? "PENDING_RETRY" : "FAILED");
            disposition = DeliveryDisposition.RETRYABLE_FAILURE;
        } else {
            delivery.setStatus("FAILED");
            disposition = DeliveryDisposition.TERMINAL_FAILURE;
        }

        deliveryRepository.save(delivery);
        if (disposition != DeliveryDisposition.RETRYABLE_FAILURE
                || attemptNumber >= WebhookDeliveryPolicy.MAX_ATTEMPTS) {
            recordProcessedEvent(eventId);
        }
        return new DeliveryOutcome(disposition, delivery.getDeliveryId(), merchantId, result.errorMessage());
    }

    /** Keeps the M6 order read model current even when this merchant has no active webhook. */
    private boolean projectMerchantOrderStatus(EventEnvelope event, UUID merchantId) {
        if (!"PARCEL_STATUS_CHANGED".equals(event.eventType()) || event.occurredAt() == null) {
            return false;
        }

        UUID parcelId;
        ParcelStatus status;
        try {
            parcelId = UUID.fromString(event.parcelId());
            status = ParcelStatus.valueOf(event.toStatus());
        } catch (IllegalArgumentException | NullPointerException exception) {
            log.warn("Invalid parcel identifiers or status in merchant webhook event");
            return false;
        }

        Optional<MerchantOrder> order = merchantOrderQuery.findByParcelId(parcelId);
        if (order.isEmpty()) {
            // The event is the source of truth for webhook routing; M6 may not own this parcel.
            return true;
        }
        MerchantOrder mappedOrder = order.get();
        if (!merchantId.equals(mappedOrder.merchantId())) {
            log.warn("Merchant identity does not match parcel order mapping for event {}", event.eventId());
            return false;
        }
        if (mappedOrder.updatedAt() == null || !event.occurredAt().isBefore(mappedOrder.updatedAt())) {
            merchantOrderQuery.updateParcelStatusByParcelId(parcelId, status, event.occurredAt());
        }
        return true;
    }

    @Transactional
    public void markDltFailed(EventEnvelope event) {
        if (event == null || event.eventId() == null || event.merchantId() == null) {
            return;
        }
        try {
            UUID eventId = UUID.fromString(event.eventId());
            UUID merchantId = UUID.fromString(event.merchantId());
            deliveryRepository.findFirstByEventIdAndMerchantIdOrderByCreatedAtAsc(eventId, merchantId)
                    .ifPresent(delivery -> {
                        if (!"SUCCESS".equals(delivery.getStatus()) && !"CANCELLED".equals(delivery.getStatus())) {
                            delivery.setStatus("FAILED");
                            delivery.setNextRetryAt(null);
                            deliveryRepository.save(delivery);
                        }
                    });
            if (!processedEventRepository.existsByEventIdAndConsumerGroup(eventId, CONSUMER_GROUP)) {
                recordProcessedEvent(eventId);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Malformed event identifiers in webhook DLT record");
        }
    }

    private WebhookDeliveryEntity newDelivery(EventEnvelope event, UUID eventId, UUID merchantId,
                                               WebhookEndpointEntity endpoint) {
        UUID deliveryId = UUID.randomUUID();
        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        WebhookPayload payload = new WebhookPayload(deliveryId, eventId, event.eventType(), occurredAt,
                event.sequence() != null ? event.sequence() : 1L, merchantId,
                new WebhookParcelFields(event.parcelId(), event.trackingNumber(), event.toStatus(),
                        event.fromStatus(), event.toStatus(), null, null, null, occurredAt, event.reason()));
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize webhook payload");
        }
        WebhookDeliveryEntity delivery = new WebhookDeliveryEntity();
        delivery.setDeliveryId(deliveryId);
        delivery.setEventId(eventId);
        delivery.setMerchantId(merchantId);
        delivery.setEventType(event.eventType());
        delivery.setTrackingNumber(event.trackingNumber());
        delivery.setTargetUrl(endpoint.getUrl());
        delivery.setStatus("PENDING");
        delivery.setAttempts(0);
        delivery.setPayload(payloadJson);
        delivery.setCreatedAt(Instant.now());
        return delivery;
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
                Map<String, String> savedHeaders = objectMapper.readValue(
                        entity.getResponseHeaders(), new TypeReference<>() {});
                responseHeaders = savedHeaders.entrySet().stream()
                        .filter(entry -> "content-type".equalsIgnoreCase(entry.getKey()))
                        .map(entry -> entry.getValue().split(";", 2)[0].trim())
                        .filter(value -> value.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+"))
                        .collect(java.util.stream.Collectors.toMap(value -> "Content-Type", value -> value));
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
                        safeFailureSummary(attempt.getErrorMessage())
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
                null,
                entity.getLastAttemptAt(),
                entity.getNextRetryAt(),
                history,
                entity.getCreatedAt()
        );
    }

    private String safeFailureSummary(String message) {
        if (message == null) {
            return null;
        }
        if (message.matches("Target returned HTTP [0-9]{3}")) {
            return message;
        }
        if ("Webhook DNS resolution failed".equals(message)
                || "Webhook destination rejected".equals(message)) {
            return message;
        }
        return "Webhook delivery failed";
    }
}
