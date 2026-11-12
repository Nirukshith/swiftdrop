package com.swiftdrop.operations.courier.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryProofAuditRepository extends JpaRepository<DeliveryProofAuditLogEntity, UUID> {

    List<DeliveryProofAuditLogEntity> findByTargetIdOrderByOccurredAtDesc(String targetId);

    List<DeliveryProofAuditLogEntity> findByActionOrderByOccurredAtDesc(String action);
}

