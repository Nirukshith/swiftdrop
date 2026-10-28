package com.swiftdrop.integration.merchant;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantOrderRepository extends JpaRepository<MerchantOrderEntity, UUID> {
    Page<MerchantOrderEntity> findAllByMerchantId(UUID merchantId, Pageable pageable);
    Page<MerchantOrderEntity> findAllByMerchantIdAndStatus(UUID merchantId, MerchantOrderStatus status, Pageable pageable);
    Optional<MerchantOrderEntity> findByOrderIdAndMerchantId(UUID orderId, UUID merchantId);
    Optional<MerchantOrderEntity> findByParcelId(UUID parcelId);
    boolean existsByMerchantIdAndExternalReference(UUID merchantId, String externalReference);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MerchantOrderEntity order set order.status = :status, order.updatedAt = :occurredAt where order.parcelId = :parcelId")
    int updateParcelStatusByParcelId(@Param("parcelId") UUID parcelId,
                                     @Param("status") MerchantOrderStatus status,
                                     @Param("occurredAt") Instant occurredAt);
}
