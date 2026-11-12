package com.swiftdrop.auth.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Merchant-service-only recovery boundary; see merchant-recovery.yaml (#207). */
@RestController
@RequestMapping("/internal/v1/merchant-provisioning")
public class MerchantProvisioningController {
    private final MerchantProvisioningService service;

    public MerchantProvisioningController(MerchantProvisioningService service) { this.service = service; }

    @PutMapping("/{operationId}")
    public ResponseEntity<MerchantProvisioningService.Result> provision(@PathVariable UUID operationId,
            @Valid @RequestBody ProvisionRequest request) {
        return secretResponse(service.provision(operationId, request));
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<MerchantProvisioningService.Result> status(@PathVariable UUID operationId) {
        return secretResponse(service.status(operationId));
    }

    @PostMapping("/{operationId}/credentials")
    public ResponseEntity<MerchantProvisioningService.Result> replace(@PathVariable UUID operationId,
            @Valid @RequestBody ReplacementRequest request) {
        return secretResponse(service.replace(operationId, request));
    }

    private ResponseEntity<MerchantProvisioningService.Result> secretResponse(MerchantProvisioningService.Result result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    public record ProvisionRequest(@NotNull UUID merchantId, @NotBlank @Email @Size(max=255) String email,
            @NotBlank @Size(max=200) String fullName, @NotBlank @Size(max=32) String phoneNumber,
            @Size(max=64) String expectedUserId, @NotBlank @Size(max=64) String actorId) {}

    public record ReplacementRequest(@NotNull UUID replacementId, @NotBlank @Size(max=64) String actorId,
            @NotNull @AssertTrue Boolean confirmReplacement) {}
}
