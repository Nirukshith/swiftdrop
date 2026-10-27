package com.swiftdrop.parcel.parcel;

import com.swiftdrop.parcel.address.District;
import com.swiftdrop.parcel.internal.InternalCreateParcelRequest;
import com.swiftdrop.parcel.internal.ParcelStatusUpdateRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class ParcelService {

    private final ParcelRepository parcelRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final TrackingNumberGenerator trackingNumberGenerator;
    private final PricingQuoteClient pricingQuoteClient;
    private final ApplicationEventPublisher eventPublisher;

    public ParcelService(
            ParcelRepository parcelRepository,
            StatusHistoryRepository statusHistoryRepository,
            TrackingNumberGenerator trackingNumberGenerator,
            PricingQuoteClient pricingQuoteClient,
            ApplicationEventPublisher eventPublisher
    ) {
        this.parcelRepository = parcelRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.trackingNumberGenerator = trackingNumberGenerator;
        this.pricingQuoteClient = pricingQuoteClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ParcelResponse createCustomerParcel(String customerId, CreateCustomerParcelRequest request) {
        validateDistricts(request.pickupAddress().district(), request.dropoffAddress().district());

        BigDecimal fee = pricingQuoteClient.fetchQuote(
                request.pickupAddress().district(),
                request.dropoffAddress().district(),
                request.weightKg(),
                request.size()
        );

        Parcel parcel = new Parcel();
        parcel.setTrackingNumber(trackingNumberGenerator.generate());
        parcel.setStatus(ParcelStatus.CREATED);
        parcel.setCustomerId(customerId);
        parcel.setPickupAddress(AddressSnapshot.from(request.pickupAddress()));
        parcel.setDropoffAddress(AddressSnapshot.from(request.dropoffAddress()));
        parcel.setWeightKg(request.weightKg());
        parcel.setSize(request.size());
        parcel.setFee(fee);
        parcel.setRecipientName(request.recipientName().trim());
        parcel.setRecipientPhone(request.recipientPhone().trim());
        parcel.setNotes(request.notes());
        parcel.setSequence(1L);
        parcel.setAttemptCount(0);

        Parcel saved = parcelRepository.save(parcel);

        StatusHistory initialHistory = new StatusHistory(
                saved.getId(),
                ParcelStatus.CREATED,
                ParcelStatus.CREATED,
                customerId,
                "Parcel order created"
        );
        statusHistoryRepository.save(initialHistory);

        eventPublisher.publishEvent(ParcelDomainEvent.created(saved));

        return ParcelResponse.from(saved);
    }

    @Transactional
    public ParcelResponse createMerchantParcel(InternalCreateParcelRequest request) {
        validateDistricts(request.pickupAddress().district(), request.dropoffAddress().district());

        Parcel parcel = new Parcel();
        parcel.setTrackingNumber(trackingNumberGenerator.generate());
        parcel.setStatus(ParcelStatus.CREATED);
        parcel.setMerchantId(request.merchantId());
        parcel.setPickupAddress(AddressSnapshot.from(request.pickupAddress()));
        parcel.setDropoffAddress(AddressSnapshot.from(request.dropoffAddress()));
        parcel.setWeightKg(request.weightKg());
        parcel.setSize(request.size());
        parcel.setFee(request.fee());
        parcel.setRecipientName(request.recipientName().trim());
        parcel.setRecipientPhone(request.recipientPhone().trim());
        parcel.setNotes(request.externalReference() != null ? "ExternalRef: " + request.externalReference() : null);
        parcel.setSequence(1L);
        parcel.setAttemptCount(0);

        Parcel saved = parcelRepository.save(parcel);

        StatusHistory initialHistory = new StatusHistory(
                saved.getId(),
                ParcelStatus.CREATED,
                ParcelStatus.CREATED,
                request.merchantId(),
                "Merchant order created"
        );
        statusHistoryRepository.save(initialHistory);

        eventPublisher.publishEvent(ParcelDomainEvent.created(saved));

        return ParcelResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ParcelResponse getParcel(String id, String requestingUserId, boolean isInternalOrAdmin) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found with id: " + id));

        if (!isInternalOrAdmin && requestingUserId != null) {
            if (parcel.getCustomerId() != null && !parcel.getCustomerId().equals(requestingUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: you do not own this parcel");
            }
        }
        return ParcelResponse.from(parcel);
    }

    @Transactional(readOnly = true)
    public ParcelPageResponse listCustomerParcels(String customerId, ParcelStatus status, Pageable pageable) {
        Page<Parcel> page = (status != null)
                ? parcelRepository.findByCustomerIdAndStatus(customerId, status, pageable)
                : parcelRepository.findByCustomerId(customerId, pageable);
        return ParcelPageResponse.from(page);
    }

    @Transactional
    public ParcelResponse cancelParcel(String id, String requestingUserId, String reason) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found with id: " + id));

        if (requestingUserId != null && parcel.getCustomerId() != null && !parcel.getCustomerId().equals(requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: you do not own this parcel");
        }

        if (parcel.getStatus() != ParcelStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot cancel parcel in status " + parcel.getStatus() + ". Cancellation permitted only while CREATED (before pickup).");
        }

        ParcelStatus oldStatus = parcel.getStatus();
        ParcelStateMachine.validateTransition(oldStatus, ParcelStatus.CANCELLED);

        parcel.setStatus(ParcelStatus.CANCELLED);
        parcel.setSequence(parcel.getSequence() + 1);
        parcel.setFailureReason(reason != null ? reason : "Cancelled by customer");

        Parcel saved = parcelRepository.save(parcel);

        StatusHistory history = new StatusHistory(
                saved.getId(),
                oldStatus,
                ParcelStatus.CANCELLED,
                requestingUserId,
                reason != null ? reason : "Cancelled before pickup"
        );
        statusHistoryRepository.save(history);

        eventPublisher.publishEvent(ParcelDomainEvent.statusChanged(saved, oldStatus, ParcelStatus.CANCELLED, reason, null));

        return ParcelResponse.from(saved);
    }

    @Transactional
    public ParcelResponse assignCourier(String id, String courierId, String assignedBy) {
        return assignCourier(id, courierId, assignedBy, false);
    }

    @Transactional
    public ParcelResponse assignCourier(String id, String courierId, String assignedBy, boolean allowReassignment) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found with id: " + id));

        if (parcel.getStatus() != ParcelStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot assign courier to parcel in status " + parcel.getStatus() + ". Parcel must be in CREATED status.");
        }

        String previousCourier = parcel.getCourierId();
        if (previousCourier != null && !previousCourier.isBlank()) {
            if (!allowReassignment) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Parcel has already been assigned to courier " + previousCourier);
            }
        }

        parcel.setCourierId(courierId);
        parcel.setSequence(parcel.getSequence() + 1);

        Parcel saved = parcelRepository.save(parcel);

        StatusHistory history = new StatusHistory(
                saved.getId(),
                ParcelStatus.CREATED,
                ParcelStatus.CREATED,
                assignedBy,
                previousCourier != null ? "Reassigned to courier " + courierId + " from " + previousCourier : "Assigned to courier " + courierId
        );
        statusHistoryRepository.save(history);

        eventPublisher.publishEvent(ParcelDomainEvent.assigned(saved, previousCourier));

        return ParcelResponse.from(saved);
    }

    @Transactional
    public ParcelResponse updateStatus(String id, ParcelStatusUpdateRequest request, String changedBy) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found with id: " + id));

        // Idempotent retry check: if already reached target status, return 200 OK without re-executing
        if (parcel.getStatus() == request.status()) {
            return ParcelResponse.from(parcel);
        }

        // Concurrency check against expected status
        if (parcel.getStatus() != request.expectedCurrentStatus()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Status conflict: expected status " + request.expectedCurrentStatus() + " but actual status is " + parcel.getStatus());
        }

        ParcelStatus oldStatus = parcel.getStatus();
        try {
            ParcelStateMachine.validateTransition(oldStatus, request.status());
        } catch (IllegalStateTransitionException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }

        // Re-queue rule per Decision 005: FAILED -> CREATED clears courier and increments attemptCount
        String previousCourier = null;
        if (ParcelStateMachine.isRequeue(oldStatus, request.status())) {
            previousCourier = parcel.getCourierId();
            parcel.setCourierId(null);
            parcel.setAttemptCount(parcel.getAttemptCount() + 1);
            parcel.setFailureReason(null);
        }

        if (request.status() == ParcelStatus.FAILED) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failure reason is required when marking parcel as FAILED");
            }
            parcel.setFailureReason(request.reason());
        }

        if (request.status() == ParcelStatus.DELIVERED && request.proof() != null) {
            parcel.setProof(request.proof());
        }

        parcel.setStatus(request.status());
        parcel.setSequence(parcel.getSequence() + 1);

        Parcel saved = parcelRepository.save(parcel);

        StatusHistory history = new StatusHistory(
                saved.getId(),
                oldStatus,
                request.status(),
                changedBy != null ? changedBy : request.courierId(),
                request.reason()
        );
        statusHistoryRepository.save(history);

        eventPublisher.publishEvent(ParcelDomainEvent.statusChanged(saved, oldStatus, request.status(), request.reason(), previousCourier));

        return ParcelResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ParcelPageResponse queryInternalParcels(
            Boolean unassigned,
            String zone,
            String courierId,
            LocalDate date,
            ParcelStatus status,
            Pageable pageable
    ) {
        Specification<Parcel> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (Boolean.TRUE.equals(unassigned)) {
                predicates.add(cb.isNull(root.get("courierId")));
                predicates.add(cb.equal(root.get("status"), ParcelStatus.CREATED));
            }

            if (courierId != null && !courierId.isBlank()) {
                predicates.add(cb.equal(root.get("courierId"), courierId));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (zone != null && !zone.isBlank()) {
                Predicate pickupMatch = cb.like(cb.upper(root.get("pickupAddress").get("district")), "%" + zone.toUpperCase() + "%");
                Predicate dropoffMatch = cb.like(cb.upper(root.get("dropoffAddress").get("district")), "%" + zone.toUpperCase() + "%");
                predicates.add(cb.or(pickupMatch, dropoffMatch));
            }

            if (date != null) {
                var startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant();
                var endOfDay = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startOfDay));
                predicates.add(cb.lessThan(root.get("createdAt"), endOfDay));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Parcel> page = parcelRepository.findAll(spec, pageable);
        return ParcelPageResponse.from(page);
    }

    private void validateDistricts(String pickupDistrict, String dropoffDistrict) {
        if (!District.isValid(pickupDistrict)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid pickup district '" + pickupDistrict + "'. Must match one of the 25 administrative districts of Sri Lanka.");
        }
        if (!District.isValid(dropoffDistrict)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid dropoff district '" + dropoffDistrict + "'. Must match one of the 25 administrative districts of Sri Lanka.");
        }
    }
}
