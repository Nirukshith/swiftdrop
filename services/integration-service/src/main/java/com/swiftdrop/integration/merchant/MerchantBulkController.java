package com.swiftdrop.integration.merchant;

import com.swiftdrop.common.ErrorResponse;
import com.swiftdrop.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MerchantBulkController {
    private final MerchantBulkService bulk;
    private final MerchantRepository merchants;
    public MerchantBulkController(MerchantBulkService bulk, MerchantRepository merchants) {
        this.bulk = bulk; this.merchants = merchants;
    }

    @PostMapping(value = "/api/v1/merchant/orders/bulk", consumes = "text/csv")
    public ResponseEntity<MerchantBulkResponse> submit(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                       HttpServletRequest request) throws IOException {
        CurrentUser user = CurrentUser.require();
        UUID merchant;
        try {
            if (!user.isMerchant() || user.merchantId() == null || user.userId() == null || user.userId().isBlank()) throw new IllegalArgumentException();
            merchant = UUID.fromString(user.merchantId());
        } catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant credentials are required"); }
        if (merchants.findById(merchant).filter(m -> m.getStatus().canPlaceOrders()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An approved merchant is required");
        }
        if (key == null || key.isBlank() || key.length() > 128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1 to 128 characters");
        if (request.getContentLengthLong() > MerchantCsv.MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "CSV exceeds 5 MiB");
        byte[] bytes = request.getInputStream().readNBytes(MerchantCsv.MAX_BYTES + 1);
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(bulk.submit(merchant, user.userId(), key, bytes));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> error(ResponseStatusException exception, HttpServletRequest request) {
        var builder = ResponseEntity.status(exception.getStatusCode());
        if (exception.getStatusCode().value() == 429) builder.header("Retry-After", "60");
        String correlation = org.slf4j.MDC.get("correlationId");
        if (correlation == null) correlation = request.getHeader("X-Correlation-ID");
        return builder.body(new ErrorResponse(exception.getStatusCode().toString(), exception.getReason(),
                correlation == null ? "N/A" : correlation, Instant.now()));
    }
}
