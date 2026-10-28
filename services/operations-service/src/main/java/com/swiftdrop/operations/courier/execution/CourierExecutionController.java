package com.swiftdrop.operations.courier.execution;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.operations.courier.execution.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courier")
public class CourierExecutionController {

    private final CourierExecutionService courierExecutionService;

    public CourierExecutionController(CourierExecutionService courierExecutionService) {
        this.courierExecutionService = courierExecutionService;
    }

    @GetMapping("/deliveries")
    public ResponseEntity<CourierDeliveriesResponse> getTodaysDeliveries(
        @RequestParam(name = "date", required = false) String date,
        @RequestParam(name = "status", required = false) String status,
        Principal principal
    ) {
        String courierId = resolveCourierId(principal);
        CourierDeliveriesResponse response = courierExecutionService.getTodaysDeliveries(courierId, date, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/deliveries/{parcelId}")
    public ResponseEntity<CourierDeliveryDetail> getDeliveryDetail(
        @PathVariable("parcelId") UUID parcelId,
        Principal principal
    ) {
        String courierId = resolveCourierId(principal);
        CourierDeliveryDetail detail = courierExecutionService.getDeliveryDetail(courierId, parcelId);
        return ResponseEntity.ok(detail);
    }

    @PatchMapping("/deliveries/{parcelId}/status")
    public ResponseEntity<CourierStatusUpdateResponse> updateDeliveryStatus(
        @PathVariable("parcelId") UUID parcelId,
        @Valid @RequestBody CourierStatusUpdateRequest request,
        Principal principal
    ) {
        String courierId = resolveCourierId(principal);
        CourierStatusUpdateResponse response = courierExecutionService.updateDeliveryStatus(courierId, parcelId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/deliveries/{parcelId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProofUploadResponse> uploadProof(
        @PathVariable("parcelId") UUID parcelId,
        @RequestParam("file") MultipartFile file,
        @RequestParam(name = "recipientName", required = false) String recipientName,
        @RequestParam(name = "notes", required = false) String notes,
        Principal principal
    ) {
        String courierId = resolveCourierId(principal);
        ProofUploadResponse response = courierExecutionService.uploadProof(courierId, parcelId, file, recipientName, notes);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String resolveCourierId(Principal principal) {
        return CurrentUser.get()
            .map(CurrentUser::userId)
            .filter(id -> !id.isBlank())
            .or(() -> Optional.ofNullable(principal).map(Principal::getName))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated courier identity is required"));
    }
}
