package com.swiftdrop.integration.webhook;

import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    private UUID resolveMerchantId(String headerMerchantId) {
        if (headerMerchantId != null && !headerMerchantId.isBlank()) {
            try {
                return UUID.fromString(headerMerchantId);
            } catch (IllegalArgumentException ignored) {
            }
        }

        Optional<CurrentUser> userOpt = CurrentUser.get();
        if (userOpt.isPresent() && userOpt.get().merchantId() != null && !userOpt.get().merchantId().isBlank()) {
            try {
                return UUID.fromString(userOpt.get().merchantId());
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Fallback for tests or token subject as UUID
        if (userOpt.isPresent() && userOpt.get().userId() != null) {
            try {
                return UUID.fromString(userOpt.get().userId());
            } catch (IllegalArgumentException ignored) {
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access forbidden. Valid merchant identification required");
    }

    @GetMapping("/settings")
    public ResponseEntity<WebhookSettingsResponse> getSettings(
            @RequestHeader(value = "X-Merchant-Id", required = false) String headerMerchantId) {
        UUID merchantId = resolveMerchantId(headerMerchantId);
        WebhookSettingsResponse response = webhookService.getSettings(merchantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/settings")
    public ResponseEntity<WebhookSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateWebhookSettingsRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false) String headerMerchantId) {
        UUID merchantId = resolveMerchantId(headerMerchantId);
        WebhookSettingsResponse response = webhookService.updateSettings(merchantId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test")
    public ResponseEntity<TestWebhookResultResponse> triggerTest(
            @RequestBody(required = false) TestWebhookRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false) String headerMerchantId) {
        UUID merchantId = resolveMerchantId(headerMerchantId);
        TestWebhookResultResponse result = webhookService.triggerTestWebhook(merchantId, request);

        if (result.success()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result);
        }
    }

    @GetMapping("/deliveries")
    public ResponseEntity<WebhookDeliveryLogPage> listDeliveries(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Merchant-Id", required = false) String headerMerchantId) {
        UUID merchantId = resolveMerchantId(headerMerchantId);
        WebhookDeliveryLogPage pageResult = webhookService.listDeliveries(merchantId, status, eventType, from, to, page, size);
        return ResponseEntity.ok(pageResult);
    }

    @GetMapping("/deliveries/{deliveryId}")
    public ResponseEntity<WebhookDeliveryDetailResponse> getDeliveryDetail(
            @PathVariable UUID deliveryId,
            @RequestHeader(value = "X-Merchant-Id", required = false) String headerMerchantId) {
        UUID merchantId = resolveMerchantId(headerMerchantId);
        WebhookDeliveryDetailResponse detail = webhookService.getDeliveryDetail(merchantId, deliveryId);
        return ResponseEntity.ok(detail);
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    public ResponseEntity<WebhookDeliveryDetailResponse> retryDelivery(
            @PathVariable UUID deliveryId,
            @RequestHeader(value = "X-Merchant-Id", required = false) String headerMerchantId) {
        UUID merchantId = resolveMerchantId(headerMerchantId);
        WebhookDeliveryDetailResponse updated = webhookService.retryDelivery(merchantId, deliveryId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(updated);
    }
}
