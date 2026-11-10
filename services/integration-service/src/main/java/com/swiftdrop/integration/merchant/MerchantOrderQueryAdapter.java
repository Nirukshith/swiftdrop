package com.swiftdrop.integration.merchant;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
        return findOrders(merchantId, status, null, null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MerchantOrder> findOrders(UUID merchantId, MerchantOrderStatus status, String trackingNumber,
                                          Instant from, Instant to, Pageable pageable) {
        Specification<MerchantOrderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("merchantId"), merchantId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (trackingNumber != null && !trackingNumber.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("trackingNumber")), "%" + trackingNumber.trim().toLowerCase() + "%"));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return repository.findAll(spec, pageable).map(MerchantOrderEntity::toReadModel);
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantOrderStatusCounts countOrdersByStatus(UUID merchantId) {
        List<Object[]> rows = repository.countOrdersByStatusGrouped(merchantId);
        Map<MerchantOrderStatus, Long> countsByStatus = new EnumMap<>(MerchantOrderStatus.class);
        long total = 0;

        for (Object[] row : rows) {
            MerchantOrderStatus status = (MerchantOrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            countsByStatus.put(status, count);
            total += count;
        }

        return new MerchantOrderStatusCounts(
                total,
                countsByStatus.getOrDefault(MerchantOrderStatus.CREATED, 0L),
                countsByStatus.getOrDefault(MerchantOrderStatus.PICKED_UP, 0L),
                countsByStatus.getOrDefault(MerchantOrderStatus.IN_TRANSIT, 0L),
                countsByStatus.getOrDefault(MerchantOrderStatus.OUT_FOR_DELIVERY, 0L),
                countsByStatus.getOrDefault(MerchantOrderStatus.DELIVERED, 0L),
                countsByStatus.getOrDefault(MerchantOrderStatus.FAILED, 0L),
                countsByStatus.getOrDefault(MerchantOrderStatus.CANCELLED, 0L)
        );
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
