package com.swiftdrop.operations.dispatch;

import com.swiftdrop.operations.dispatch.client.*;
import com.swiftdrop.operations.dispatch.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class DispatchService {

    private final ParcelServiceClient parcelServiceClient;
    private final CourierDirectory courierDirectory;
    private final CourierWorkloadRepository courierWorkloadRepository;

    public DispatchService(
        ParcelServiceClient parcelServiceClient,
        CourierDirectory courierDirectory,
        CourierWorkloadRepository courierWorkloadRepository
    ) {
        this.parcelServiceClient = parcelServiceClient;
        this.courierDirectory = courierDirectory;
        this.courierWorkloadRepository = courierWorkloadRepository;
    }

    @Transactional(readOnly = true)
    public UnassignedParcelsPage listUnassignedParcels(String zone, LocalDate date, int page, int size) {
        ParcelPageClientResponse response = parcelServiceClient.queryUnassignedParcels(zone, date, page, size);
        if (response == null || response.content() == null) {
            return new UnassignedParcelsPage(List.of(), 0, 0, page, size);
        }

        List<DispatchParcelSummary> summaries = response.content().stream()
            .map(this::mapToSummary)
            .toList();

        return new UnassignedParcelsPage(
            summaries,
            response.totalElements(),
            response.totalPages(),
            response.page(),
            response.size()
        );
    }

    @Transactional(readOnly = true)
    public FailedParcelsPage listFailedParcels(String zone, LocalDate date, int page, int size) {
        ParcelPageClientResponse response = parcelServiceClient.queryFailedParcels(zone, date, page, size);
        if (response == null || response.content() == null) {
            return new FailedParcelsPage(List.of(), 0, 0, page, size);
        }

        List<DispatchParcelSummary> summaries = response.content().stream()
            .map(this::mapToSummary)
            .toList();

        return new FailedParcelsPage(
            summaries,
            response.totalElements(),
            response.totalPages(),
            response.page(),
            response.size()
        );
    }

    @Transactional
    public RequeueResponse requeueParcel(String parcelId, String reason, String requeuedBy) {
        ParcelClientResponse response = parcelServiceClient.requeueParcel(parcelId, reason, requeuedBy);
        return new RequeueResponse(
            response.id(),
            response.status(),
            response.attemptCount(),
            response.updatedAt() != null ? response.updatedAt() : Instant.now()
        );
    }

    public BulkAssignResponse bulkAssignParcels(BulkAssignRequest request, String assignedBy) {
        if (request == null || request.assignments() == null || request.assignments().isEmpty()) {
            return new BulkAssignResponse(0, 0, 0, List.of());
        }

        List<AssignParcelRequest> assignments = request.assignments();
        List<BulkAssignItemResult> results = new ArrayList<>();
        Set<String> seenParcelIds = new HashSet<>();
        int successCount = 0;
        int failCount = 0;

        for (AssignParcelRequest item : assignments) {
            String parcelId = item.parcelId();
            String courierId = item.courierId();

            if (parcelId != null && !seenParcelIds.add(parcelId)) {
                results.add(new BulkAssignItemResult(
                    parcelId,
                    courierId,
                    false,
                    "DUPLICATE_PARCEL_IN_BATCH",
                    "Duplicate parcel in batch request"
                ));
                failCount++;
                continue;
            }

            try {
                Optional<CourierSummary> courierOpt = courierDirectory.findCourierById(courierId);
                if (courierOpt.isEmpty()) {
                    results.add(new BulkAssignItemResult(
                        parcelId,
                        courierId,
                        false,
                        "COURIER_NOT_FOUND",
                        "Courier not found with id: " + courierId
                    ));
                    failCount++;
                    continue;
                }

                if (!courierOpt.get().available()) {
                    results.add(new BulkAssignItemResult(
                        parcelId,
                        courierId,
                        false,
                        "COURIER_UNAVAILABLE",
                        "Courier is currently unavailable for assignments."
                    ));
                    failCount++;
                    continue;
                }

                boolean reassign = Boolean.TRUE.equals(item.reassign());
                parcelServiceClient.assignCourier(parcelId, courierId, assignedBy, reassign);
                results.add(new BulkAssignItemResult(
                    parcelId,
                    courierId,
                    true,
                    null,
                    null
                ));
                successCount++;
            } catch (ParcelAlreadyAssignedException ex) {
                results.add(new BulkAssignItemResult(
                    parcelId,
                    courierId,
                    false,
                    "PARCEL_ALREADY_ASSIGNED",
                    ex.getMessage()
                ));
                failCount++;
            } catch (ParcelNotFoundException ex) {
                results.add(new BulkAssignItemResult(
                    parcelId,
                    courierId,
                    false,
                    "PARCEL_NOT_FOUND",
                    ex.getMessage()
                ));
                failCount++;
            } catch (Exception ex) {
                results.add(new BulkAssignItemResult(
                    parcelId,
                    courierId,
                    false,
                    "ASSIGNMENT_FAILED",
                    ex.getMessage() != null ? ex.getMessage() : "Unknown assignment failure"
                ));
                failCount++;
            }
        }

        return new BulkAssignResponse(
            assignments.size(),
            successCount,
            failCount,
            results
        );
    }

    @Transactional
    public AssignmentResult assignParcel(AssignParcelRequest request, String assignedBy) {
        Optional<CourierSummary> courierOpt = courierDirectory.findCourierById(request.courierId());
        if (courierOpt.isEmpty()) {
            throw new CourierNotFoundException("Courier not found with id: " + request.courierId());
        }

        if (!courierOpt.get().available()) {
            throw new CourierUnavailableException("Courier is currently unavailable for assignments.");
        }

        boolean reassign = Boolean.TRUE.equals(request.reassign());
        parcelServiceClient.assignCourier(request.parcelId(), request.courierId(), assignedBy, reassign);

        return new AssignmentResult(
            request.parcelId(),
            request.courierId(),
            Instant.now(),
            "ASSIGNED"
        );
    }

    @Transactional(readOnly = true)
    public List<CourierWorkloadItem> getCourierWorkloadBoard(String zone, Boolean availableOnly) {
        boolean filterAvailable = Boolean.TRUE.equals(availableOnly);
        List<CourierSummary> couriers;

        if (zone != null && !zone.isBlank()) {
            couriers = courierDirectory.listCouriersByZone(zone, filterAvailable);
        } else {
            couriers = courierDirectory.listAllCouriers();
            if (filterAvailable) {
                couriers = couriers.stream().filter(CourierSummary::available).toList();
            }
        }

        return couriers.stream()
            .map(c -> {
                int activeCount = courierWorkloadRepository.findByCourierId(c.courierId())
                    .map(CourierWorkloadEntity::getActiveCount)
                    .orElse(0);

                return new CourierWorkloadItem(
                    c.courierId(),
                    c.name(),
                    c.phoneNumber(),
                    c.zone(),
                    activeCount,
                    c.available(),
                    c.vehicleType()
                );
            })
            .toList();
    }

    @Transactional
    public CourierWorkloadItem updateCourierAvailability(String courierId, boolean available) {
        Optional<CourierSummary> courierOpt = courierDirectory.findCourierById(courierId);
        if (courierOpt.isEmpty()) {
            throw new CourierNotFoundException("Courier not found with id: " + courierId);
        }

        courierDirectory.updateAvailability(courierId, available);

        CourierSummary updated = courierDirectory.findCourierById(courierId).orElse(courierOpt.get());
        int activeCount = courierWorkloadRepository.findByCourierId(courierId)
            .map(CourierWorkloadEntity::getActiveCount)
            .orElse(0);

        return new CourierWorkloadItem(
            updated.courierId(),
            updated.name(),
            updated.phoneNumber(),
            updated.zone(),
            activeCount,
            updated.available(),
            updated.vehicleType()
        );
    }

    private DispatchParcelSummary mapToSummary(ParcelClientResponse p) {
        String pickupZone = p.pickupAddress() != null && p.pickupAddress().district() != null
            ? p.pickupAddress().district()
            : "";
        String dropoffZone = p.dropoffAddress() != null && p.dropoffAddress().district() != null
            ? p.dropoffAddress().district()
            : "";

        return new DispatchParcelSummary(
            p.id(),
            p.trackingNumber(),
            p.status(),
            pickupZone,
            dropoffZone,
            formatAddress(p.pickupAddress()),
            formatAddress(p.dropoffAddress()),
            p.weightKg(),
            p.attemptCount(),
            p.failureReason(),
            p.createdAt()
        );
    }

    private String formatAddress(AddressClientDto addr) {
        if (addr == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (addr.line1() != null) sb.append(addr.line1());
        if (addr.line2() != null && !addr.line2().isBlank()) sb.append(", ").append(addr.line2());
        if (addr.city() != null && !addr.city().isBlank()) sb.append(", ").append(addr.city());
        if (addr.district() != null && !addr.district().isBlank()) sb.append(", ").append(addr.district());
        return sb.toString();
    }
}
