package com.swiftdrop.integration.merchant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MerchantRecoveryController {
    private final MerchantRecoveryService recovery;
    public MerchantRecoveryController(MerchantRecoveryService recovery) { this.recovery = recovery; }

    @PostMapping("/api/v1/admin/merchants/{merchantId}/recover-credentials")
    public ResponseEntity<MerchantApprovalResponse> recover(@PathVariable UUID merchantId,
            @RequestHeader("Idempotency-Key") UUID replacementId,
            @Valid @RequestBody Confirmation request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role is required");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(recovery.recover(merchantId, replacementId));
    }
    public record Confirmation(@NotNull @AssertTrue Boolean confirmReplacement) {}
}
