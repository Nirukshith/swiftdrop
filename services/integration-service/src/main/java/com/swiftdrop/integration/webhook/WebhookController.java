package com.swiftdrop.integration.webhook;

import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    private UUID requireMerchantId() {
        CurrentUser user = CurrentUser.require();
        if (!user.isMerchant() || user.merchantId() == null || user.merchantId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant credentials are required");
        }
        try {
            return UUID.fromString(user.merchantId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant credentials are invalid");
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<WebhookSettingsResponse> getSettings() {
        UUID merchantId = requireMerchantId();
        WebhookSettingsResponse response = webhookService.getSettings(merchantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/settings")
    public ResponseEntity<WebhookSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateWebhookSettingsRequest request) {
        UUID merchantId = requireMerchantId();
        WebhookSettingsResponse response = webhookService.updateSettings(merchantId, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/test")
    public ResponseEntity<TestWebhookResultResponse> triggerTest(
            @RequestBody(required = false) TestWebhookRequest request) {
        UUID merchantId = requireMerchantId();
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
            @RequestParam(defaultValue = "20") int size) {
        UUID merchantId = requireMerchantId();
        WebhookDeliveryLogPage pageResult = webhookService.listDeliveries(merchantId, status, eventType, from, to, page, size);
        return ResponseEntity.ok(pageResult);
    }

    @GetMapping("/deliveries/{deliveryId}")
    public ResponseEntity<WebhookDeliveryDetailResponse> getDeliveryDetail(
            @PathVariable UUID deliveryId) {
        UUID merchantId = requireMerchantId();
        WebhookDeliveryDetailResponse detail = webhookService.getDeliveryDetail(merchantId, deliveryId);
        return ResponseEntity.ok(detail);
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    public ResponseEntity<WebhookDeliveryDetailResponse> retryDelivery(
            @PathVariable UUID deliveryId) {
        UUID merchantId = requireMerchantId();
        WebhookDeliveryDetailResponse updated = webhookService.retryDelivery(merchantId, deliveryId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(updated);
    }
}
