package com.swiftdrop.parcel.parcel;

import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/parcels")
public class ParcelController {

    private final ParcelService parcelService;

    public ParcelController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @PostMapping
    public ResponseEntity<ParcelResponse> createParcel(@Valid @RequestBody CreateCustomerParcelRequest request) {
        String customerId = CurrentUser.require().userId();
        ParcelResponse response = parcelService.createCustomerParcel(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ParcelPageResponse> listCustomerParcels(
            @RequestParam(required = false) ParcelStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        String customerId = CurrentUser.require().userId();
        return ResponseEntity.ok(parcelService.listCustomerParcels(customerId, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParcelResponse> getParcel(@PathVariable String id) {
        String customerId = CurrentUser.require().userId();
        return ResponseEntity.ok(parcelService.getParcel(id, customerId, false));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ParcelResponse> cancelParcel(
            @PathVariable String id,
            @RequestBody(required = false) CancelParcelRequest request
    ) {
        String customerId = CurrentUser.require().userId();
        String reason = (request != null) ? request.reason() : "Cancelled by customer";
        return ResponseEntity.ok(parcelService.cancelParcel(id, customerId, reason));
    }
}
