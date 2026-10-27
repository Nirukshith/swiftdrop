package com.swiftdrop.parcel.internal;

import com.swiftdrop.parcel.parcel.ParcelPageResponse;
import com.swiftdrop.parcel.parcel.ParcelResponse;
import com.swiftdrop.parcel.parcel.ParcelService;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/internal/v1/parcels")
public class InternalParcelController {

    private final ParcelService parcelService;

    public InternalParcelController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @PutMapping("/{id}/assignment")
    public ResponseEntity<ParcelResponse> assignCourier(
            @PathVariable String id,
            @Valid @RequestBody ParcelAssignmentRequest request
    ) {
        ParcelResponse response = parcelService.assignCourier(id, request.courierId(), request.assignedBy());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ParcelResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody ParcelStatusUpdateRequest request
    ) {
        ParcelResponse response = parcelService.updateStatus(id, request, request.courierId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ParcelPageResponse> queryParcels(
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String courierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ParcelStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        ParcelPageResponse response = parcelService.queryInternalParcels(unassigned, zone, courierId, date, status, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ParcelResponse> createMerchantParcel(
            @Valid @RequestBody InternalCreateParcelRequest request
    ) {
        ParcelResponse response = parcelService.createMerchantParcel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
