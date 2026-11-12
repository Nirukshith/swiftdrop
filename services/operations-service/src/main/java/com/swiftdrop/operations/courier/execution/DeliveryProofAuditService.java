package com.swiftdrop.operations.courier.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class DeliveryProofAuditService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryProofAuditService.class);

    private final DeliveryProofAuditRepository repository;

    public DeliveryProofAuditService(DeliveryProofAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String actorId, String actorRole, String action, String targetType, String targetId, Map<String, Object> details) {
        try {
            DeliveryProofAuditLogEntity entity = DeliveryProofAuditLogEntity.builder()
                .id(UUID.randomUUID())
                .occurredAt(Instant.now())
                .actorId(actorId != null && !actorId.isBlank() ? actorId : "SYSTEM")
                .actorRole(actorRole != null && !actorRole.isBlank() ? actorRole : "COURIER")
                .action(action)
                .targetType(targetType != null && !targetType.isBlank() ? targetType : "DELIVERY_PROOF")
                .targetId(targetId)
                .details(details != null ? details : Map.of())
                .build();
            repository.save(entity);
            log.debug("Recorded delivery proof audit event id={} action={} target={}:{}", entity.getId(), action, targetType, targetId);
        } catch (Exception ex) {
            log.error("Failed to record delivery proof audit event action={}: {}", action, ex.getMessage(), ex);
            throw ex;
        }
    }
}

