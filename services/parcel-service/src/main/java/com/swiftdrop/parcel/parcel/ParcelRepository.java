package com.swiftdrop.parcel.parcel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, String>, JpaSpecificationExecutor<Parcel> {

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    Page<Parcel> findByCustomerId(String customerId, Pageable pageable);

    Page<Parcel> findByCustomerIdAndStatus(String customerId, ParcelStatus status, Pageable pageable);

    Page<Parcel> findByCourierId(String courierId, Pageable pageable);

    Page<Parcel> findByCourierIdAndStatus(String courierId, ParcelStatus status, Pageable pageable);
}
