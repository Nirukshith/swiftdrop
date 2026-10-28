package com.swiftdrop.operations.courier.profile;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.operations.courier.execution.dto.CourierDeliveriesResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courier")
public class CourierProfileController {
    private final CourierProfileOperations operations;

    public CourierProfileController(CourierProfileOperations operations) { this.operations = operations; }

    @GetMapping("/me")
    public CourierProfileResponse getProfile(Principal principal) { return operations.getProfile(courierId(principal)); }

    @PutMapping("/availability")
    public CourierProfileResponse setAvailability(@Valid @RequestBody AvailabilityUpdate request, Principal principal) {
        return operations.setAvailability(courierId(principal), request.available());
    }

    @PostMapping("/deliveries/{parcelId}/fail")
    public ResponseEntity<Void> fail(@PathVariable UUID parcelId, @Valid @RequestBody FailedDeliveryRequest request, Principal principal) {
        operations.failDelivery(courierId(principal), parcelId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/summary")
    public CourierDeliveriesResponse summary(@RequestParam(required = false) LocalDate date, Principal principal) {
        return operations.getSummary(courierId(principal), date);
    }

    @GetMapping("/proofs/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> proof(@PathVariable String id) { return operations.getProof(id); }

    private String courierId(Principal principal) {
        return CurrentUser.get().map(CurrentUser::userId)
            .filter(id -> !id.isBlank())
            .or(() -> Optional.ofNullable(principal).map(Principal::getName))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated courier identity is required"));
    }
}
