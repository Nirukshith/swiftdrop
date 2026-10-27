package com.swiftdrop.integration.merchant;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MerchantOrderQueryAdapter implements MerchantOrderQuery {

    private final MerchantOrderRepository repository;

    public MerchantOrderQueryAdapter(MerchantOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MerchantOrder> findByMerchantId(UUID merchantId, MerchantOrderStatus status, Pageable pageable) {
        Page<MerchantOrderEntity> page = status == null
                ? repository.findAllByMerchantId(merchantId, pageable)
                : repository.findAllByMerchantIdAndStatus(merchantId, status, pageable);
        return page.map(MerchantOrderEntity::toReadModel);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MerchantOrder> findByIdAndMerchantId(UUID orderId, UUID merchantId) {
        return repository.findByOrderIdAndMerchantId(orderId, merchantId).map(MerchantOrderEntity::toReadModel);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MerchantOrder> findByParcelId(UUID parcelId) {
        return repository.findByParcelId(parcelId).map(MerchantOrderEntity::toReadModel);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByMerchantIdAndExternalReference(UUID merchantId, String externalReference) {
        return repository.existsByMerchantIdAndExternalReference(merchantId, externalReference);
    }

    @Override
    @Transactional
    public int updateParcelStatusByParcelId(UUID parcelId, ParcelStatus status, Instant occurredAt) {
        return repository.updateParcelStatusByParcelId(parcelId, MerchantOrderStatus.valueOf(status.name()), occurredAt);
    }
}
