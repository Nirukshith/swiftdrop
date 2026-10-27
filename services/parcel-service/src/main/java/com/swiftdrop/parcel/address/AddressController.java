package com.swiftdrop.parcel.address;

import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<List<AddressResponse>> listAddresses() {
        String customerId = CurrentUser.require().userId();
        return ResponseEntity.ok(addressService.listAddresses(customerId));
    }

    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody CreateAddressRequest request) {
        String customerId = CurrentUser.require().userId();
        AddressResponse response = addressService.createAddress(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AddressResponse> getAddress(@PathVariable String id) {
        String customerId = CurrentUser.require().userId();
        return ResponseEntity.ok(addressService.getAddress(id, customerId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressResponse> updateAddress(
            @PathVariable String id,
            @Valid @RequestBody CreateAddressRequest request) {
        String customerId = CurrentUser.require().userId();
        return ResponseEntity.ok(addressService.updateAddress(id, customerId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable String id) {
        String customerId = CurrentUser.require().userId();
        addressService.deleteAddress(id, customerId);
        return ResponseEntity.noContent().build();
    }
}
