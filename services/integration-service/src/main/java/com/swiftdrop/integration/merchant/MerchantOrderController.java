package com.swiftdrop.integration.merchant;

import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/v1/merchant/orders")
public class MerchantOrderController {

    private final MerchantOrderService merchantOrderService;
    private final MerchantOrderQuery merchantOrderQuery;

    public MerchantOrderController(MerchantOrderService merchantOrderService,
                                   MerchantOrderQuery merchantOrderQuery) {
        this.merchantOrderService = merchantOrderService;
        this.merchantOrderQuery = merchantOrderQuery;
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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<String> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @PostMapping
    public ResponseEntity<MerchantOrderResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateMerchantOrderRequest request) {
        UUID merchantId = requireMerchantId();
        MerchantOrderIdempotencyStore.StoredResponse stored =
                merchantOrderService.create(merchantId, idempotencyKey, request);
        return ResponseEntity.status(stored.status()).body(stored.body());
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<MerchantOrderResponse> cancel(
            @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody(required = false) CancelMerchantOrderRequest request) {
        UUID merchantId = requireMerchantId();
        MerchantOrderIdempotencyStore.StoredResponse stored =
                merchantOrderService.cancel(merchantId, orderId, idempotencyKey, request);
        return ResponseEntity.status(stored.status()).body(stored.body());
    }

    @GetMapping
    public ResponseEntity<MerchantOrderListPage> listOrders(
            @RequestParam(required = false) MerchantOrderStatus status,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100");
        }

        UUID merchantId = requireMerchantId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MerchantOrder> orderPage = merchantOrderQuery.findOrders(merchantId, status, trackingNumber, from, to, pageable);

        List<MerchantOrderListItem> items = orderPage.getContent().stream()
                .map(MerchantOrderListItem::from)
                .toList();

        MerchantOrderListPage response = new MerchantOrderListPage(
                items,
                orderPage.getTotalElements(),
                orderPage.getTotalPages(),
                orderPage.getNumber(),
                orderPage.getSize()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/counts")
    public ResponseEntity<MerchantOrderCountsResponse> getOrderCounts() {
        UUID merchantId = requireMerchantId();
        MerchantOrderStatusCounts counts = merchantOrderQuery.countOrdersByStatus(merchantId);
        return ResponseEntity.ok(MerchantOrderCountsResponse.from(counts));
    }
}
