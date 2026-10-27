package com.swiftdrop.integration.merchant;

import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Validated
@RestController
@RequestMapping("/api/v1/merchant/orders")
public class MerchantOrderController {

    private final MerchantOrderService merchantOrderService;

    public MerchantOrderController(MerchantOrderService merchantOrderService) {
        this.merchantOrderService = merchantOrderService;
    }

    @PostMapping
    public ResponseEntity<MerchantOrderResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateMerchantOrderRequest request) {
        CurrentUser user = CurrentUser.require();
        if (!user.isMerchant() || user.merchantId() == null || user.merchantId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant credentials are required");
        }
        UUID merchantId;
        try {
            merchantId = UUID.fromString(user.merchantId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant credentials are invalid");
        }

        MerchantOrderIdempotencyStore.StoredResponse stored =
                merchantOrderService.create(merchantId, idempotencyKey, request);
        return ResponseEntity.status(stored.status()).body(stored.body());
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<MerchantOrderResponse> cancel(
            @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody(required = false) CancelMerchantOrderRequest request) {
        CurrentUser user = CurrentUser.require();
        if (!user.isMerchant() || user.merchantId() == null || user.merchantId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant credentials are required");
        }
        UUID merchantId;
        try {
            merchantId = UUID.fromString(user.merchantId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant credentials are invalid");
        }

        MerchantOrderIdempotencyStore.StoredResponse stored =
                merchantOrderService.cancel(merchantId, orderId, idempotencyKey, request);
        return ResponseEntity.status(stored.status()).body(stored.body());
    }
}
