package com.swiftdrop.integration.merchant;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Query port agreed for the merchant order dashboard and parcel status projection.
 */
public interface MerchantOrderQuery {

    /** Lists orders belonging to one merchant, optionally filtered by status. */
    Page<MerchantOrder> findByMerchantId(UUID merchantId, MerchantOrderStatus status, Pageable pageable);

    /** Finds an order only when it belongs to the specified merchant. */
    Optional<MerchantOrder> findByIdAndMerchantId(UUID orderId, UUID merchantId);

    /** Finds the merchant-order mapping associated with a parcel. */
    Optional<MerchantOrder> findByParcelId(UUID parcelId);

    /** Checks whether a merchant has already used its external order reference. */
    boolean existsByMerchantIdAndExternalReference(UUID merchantId, String externalReference);

    /** Updates the locally projected parcel status and observation time for a parcel. */
    int updateParcelStatusByParcelId(UUID parcelId, ParcelStatus status, Instant occurredAt);
}
