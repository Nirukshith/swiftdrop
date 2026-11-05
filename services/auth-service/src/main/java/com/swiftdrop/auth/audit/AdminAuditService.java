package com.swiftdrop.auth.audit;

import com.swiftdrop.auth.audit.dto.AuditLogPageResponse;
import com.swiftdrop.auth.audit.dto.AuditRecordResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for administrative audit log queries.
 * Owned by M8.
 */
@Service
public class AdminAuditService {

    private final AuditLogRepository auditLogRepository;

    public AdminAuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse getAuditLogs(
        String action,
        String actorId,
        String targetType,
        Instant fromDate,
        Instant toDate,
        int page,
        int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }

        Specification<AuditLogEntity> spec = createSpecification(action, actorId, targetType, fromDate, toDate);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"));

        Page<AuditLogEntity> resultPage = auditLogRepository.findAll(spec, pageRequest);

        List<AuditRecordResponse> records = resultPage.getContent().stream()
            .map(AuditRecordResponse::from)
            .toList();

        return new AuditLogPageResponse(
            records,
            resultPage.getTotalElements(),
            resultPage.getTotalPages(),
            resultPage.getNumber(),
            resultPage.getSize()
        );
    }

    private Specification<AuditLogEntity> createSpecification(
        String action,
        String actorId,
        String targetType,
        Instant fromDate,
        Instant toDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (actorId != null && !actorId.isBlank()) {
                predicates.add(cb.equal(root.get("actorId"), actorId.trim()));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType.trim()));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
