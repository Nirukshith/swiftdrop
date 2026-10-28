package com.swiftdrop.operations.courier.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryProofRepository extends JpaRepository<DeliveryProofEntity, UUID> {

    Optional<DeliveryProofEntity> findFirstByParcelIdOrderByUploadedAtDesc(UUID parcelId);

    List<DeliveryProofEntity> findByCourierIdOrderByUploadedAtDesc(String courierId);

    Optional<DeliveryProofEntity> findByIdAndCourierId(UUID id, String courierId);
}
