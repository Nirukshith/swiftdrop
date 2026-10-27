package com.swiftdrop.parcel.address;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AddressService {

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(String customerId) {
        return addressRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse getAddress(String id, String customerId) {
        Address address = addressRepository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found with id: " + id));
        return AddressResponse.from(address);
    }

    @Transactional
    public AddressResponse createAddress(String customerId, CreateAddressRequest request) {
        validateDistrict(request.district());

        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault) {
            clearCustomerDefaults(customerId);
        }

        Address address = new Address();
        address.setCustomerId(customerId);
        address.setLabel(request.label().trim());
        address.setLine1(request.line1().trim());
        address.setLine2(request.line2() != null ? request.line2().trim() : null);
        address.setCity(request.city().trim());
        address.setDistrict(District.normalize(request.district()));
        address.setPostalCode(request.postalCode().trim());
        address.setDefault(isDefault);

        Address saved = addressRepository.save(address);
        return AddressResponse.from(saved);
    }

    @Transactional
    public AddressResponse updateAddress(String id, String customerId, CreateAddressRequest request) {
        validateDistrict(request.district());

        Address address = addressRepository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found with id: " + id));

        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault && !address.isDefault()) {
            clearCustomerDefaults(customerId);
        }

        address.setLabel(request.label().trim());
        address.setLine1(request.line1().trim());
        address.setLine2(request.line2() != null ? request.line2().trim() : null);
        address.setCity(request.city().trim());
        address.setDistrict(District.normalize(request.district()));
        address.setPostalCode(request.postalCode().trim());
        address.setDefault(isDefault);

        Address updated = addressRepository.save(address);
        return AddressResponse.from(updated);
    }

    @Transactional
    public void deleteAddress(String id, String customerId) {
        Address address = addressRepository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found with id: " + id));
        addressRepository.delete(address);
    }

    private void validateDistrict(String district) {
        if (!District.isValid(district)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid district '" + district + "'. Must match one of the 25 administrative districts of Sri Lanka per ADR 002.");
        }
    }

    private void clearCustomerDefaults(String customerId) {
        List<Address> defaults = addressRepository.findByCustomerIdAndIsDefaultTrue(customerId);
        for (Address addr : defaults) {
            addr.setDefault(false);
            addressRepository.save(addr);
        }
    }
}
