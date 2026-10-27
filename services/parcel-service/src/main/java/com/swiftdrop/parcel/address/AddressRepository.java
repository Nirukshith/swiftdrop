package com.swiftdrop.parcel.address;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, String> {

    List<Address> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    Optional<Address> findByIdAndCustomerId(String id, String customerId);

    List<Address> findByCustomerIdAndIsDefaultTrue(String customerId);
}
