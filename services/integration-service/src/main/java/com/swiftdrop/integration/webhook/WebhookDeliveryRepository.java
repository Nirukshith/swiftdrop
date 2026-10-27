package com.swiftdrop.integration.webhook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, UUID>,
        JpaSpecificationExecutor<WebhookDeliveryEntity> {

    Optional<WebhookDeliveryEntity> findByDeliveryIdAndMerchantId(UUID deliveryId, UUID merchantId);

    @Query("SELECT d FROM WebhookDeliveryEntity d WHERE d.merchantId = :merchantId " +
            "AND (:status IS NULL OR d.status = :status) " +
            "AND (:eventType IS NULL OR d.eventType = :eventType) " +
            "AND (CAST(:fromDate AS timestamp) IS NULL OR d.createdAt >= :fromDate) " +
            "AND (CAST(:toDate AS timestamp) IS NULL OR d.createdAt <= :toDate)")
    Page<WebhookDeliveryEntity> findDeliveries(
            @Param("merchantId") UUID merchantId,
            @Param("status") String status,
            @Param("eventType") String eventType,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable
    );
}
