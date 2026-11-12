package com.swiftdrop.parcel.parcel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface MerchantParcelIdempotencyRepository extends JpaRepository<MerchantParcelIdempotency, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO merchant_parcel_idempotency
                (merchant_id, operation, idempotency_key, request_hash, created_at)
            VALUES (:merchantId, :operation, :idempotencyKey, :requestHash, :createdAt)
            ON CONFLICT (merchant_id, operation, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int reserve(
            @Param("merchantId") String merchantId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("createdAt") Instant createdAt
    );

    Optional<MerchantParcelIdempotency> findByMerchantIdAndOperationAndIdempotencyKey(
            String merchantId,
            String operation,
            String idempotencyKey
    );

    @Modifying
    @Query("""
            UPDATE MerchantParcelIdempotency record
            SET record.parcelId = :parcelId
            WHERE record.merchantId = :merchantId
              AND record.operation = :operation
              AND record.idempotencyKey = :idempotencyKey
            """)
    int attachParcel(
            @Param("merchantId") String merchantId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("parcelId") String parcelId
    );
}
