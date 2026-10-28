package com.swiftdrop.operations.courier.execution;

import com.swiftdrop.operations.courier.execution.client.*;
import com.swiftdrop.operations.courier.execution.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CourierExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CourierExecutionService.class);
    private static final long DEFAULT_MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    private final ParcelServiceClient parcelServiceClient;
    private final DeliveryProofRepository deliveryProofRepository;
    private final String storageDir;
    private final long maxSizeBytes;

    public CourierExecutionService(
        ParcelServiceClient parcelServiceClient,
        DeliveryProofRepository deliveryProofRepository,
        @Value("${swiftdrop.proofs.storage-dir:}") String storageDir,
        @Value("${swiftdrop.proofs.max-size-bytes:5242880}") long maxSizeBytes
    ) {
        this.parcelServiceClient = parcelServiceClient;
        this.deliveryProofRepository = deliveryProofRepository;
        if (storageDir == null || storageDir.isBlank() || "/data/proofs".equals(storageDir)) {
            this.storageDir = Paths.get(System.getProperty("java.io.tmpdir"), "swiftdrop", "proofs").toString();
        } else {
            this.storageDir = storageDir;
        }
        this.maxSizeBytes = maxSizeBytes > 0 ? maxSizeBytes : DEFAULT_MAX_SIZE_BYTES;
    }

    public CourierDeliveriesResponse getTodaysDeliveries(String courierId, String dateStr, String statusFilter) {
        String effectiveDate = (dateStr != null && !dateStr.isBlank())
            ? dateStr
            : LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        ParcelPageResponse response = parcelServiceClient.getParcelsByCourierAndDate(courierId, effectiveDate);
        List<ParcelResponse> allParcels = response != null && response.content() != null
            ? response.content()
            : List.of();

        List<CourierDeliveryCard> cards = new ArrayList<>();
        int completedCount = 0;
        int pendingCount = 0;
        int sequenceIndex = 1;

        for (ParcelResponse p : allParcels) {
            String status = p.status();
            boolean isCompleted = "DELIVERED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status);

            if (isCompleted) {
                completedCount++;
            } else {
                pendingCount++;
            }

            if (statusFilter != null && !statusFilter.isBlank() && !status.equalsIgnoreCase(statusFilter.trim())) {
                continue;
            }

            String dropoffStr = p.dropoffAddress() != null ? p.dropoffAddress().toSingleLineString() : "";
            String pickupStr = p.pickupAddress() != null ? p.pickupAddress().toSingleLineString() : "";
            String mapsUrl = buildGoogleMapsUrl(p.dropoffAddress());

            cards.add(new CourierDeliveryCard(
                p.id(),
                p.trackingNumber(),
                status,
                p.recipientName(),
                p.recipientPhone(),
                pickupStr,
                dropoffStr,
                mapsUrl,
                p.weightKg(),
                p.attemptCount() != null ? p.attemptCount() : 0,
                sequenceIndex++
            ));
        }

        return new CourierDeliveriesResponse(
            effectiveDate,
            cards.size(),
            pendingCount,
            completedCount,
            cards
        );
    }

    public CourierDeliveryDetail getDeliveryDetail(String courierId, UUID parcelId) {
        ParcelResponse parcel = parcelServiceClient.getParcel(parcelId);
        if (parcel == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery parcel not found: " + parcelId);
        }

        // Ownership check
        validateCourierOwnership(parcel, courierId);

        Optional<DeliveryProofEntity> proofOpt = deliveryProofRepository.findFirstByParcelIdOrderByUploadedAtDesc(parcelId);
        DeliveryProofInfo proofInfo = proofOpt.map(p -> new DeliveryProofInfo(
            p.getId(),
            p.getProofType(),
            p.getProofUrl(),
            p.getRecipientName(),
            p.getUploadedAt()
        )).orElse(null);

        List<String> nextStatuses = calculateAllowedNextStatuses(parcel.status());
        boolean proofRequired = "OUT_FOR_DELIVERY".equalsIgnoreCase(parcel.status())
            || "DELIVERED".equalsIgnoreCase(parcel.status());

        String dropoffStr = parcel.dropoffAddress() != null ? parcel.dropoffAddress().toSingleLineString() : "";
        String pickupStr = parcel.pickupAddress() != null ? parcel.pickupAddress().toSingleLineString() : "";
        String mapsUrl = buildGoogleMapsUrl(parcel.dropoffAddress());

        return new CourierDeliveryDetail(
            parcel.id(),
            parcel.trackingNumber(),
            parcel.status(),
            parcel.recipientName(),
            parcel.recipientPhone(),
            "SwiftDrop Hub",
            "+94112000000",
            pickupStr,
            dropoffStr,
            mapsUrl,
            parcel.notes(),
            parcel.weightKg(),
            parcel.attemptCount() != null ? parcel.attemptCount() : 0,
            nextStatuses,
            proofRequired,
            proofInfo,
            parcel.failureReason()
        );
    }

    public CourierStatusUpdateResponse updateDeliveryStatus(
        String courierId,
        UUID parcelId,
        CourierStatusUpdateRequest request
    ) {
        ParcelResponse parcel = parcelServiceClient.getParcel(parcelId);
        if (parcel == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery parcel not found: " + parcelId);
        }

        // 1. Ownership check: courier B cannot update courier A's delivery
        validateCourierOwnership(parcel, courierId);

        String currentStatus = parcel.status();
        String targetStatus = request.status().toUpperCase(Locale.ROOT);

        // 2. Idempotency / Safe retries
        if (targetStatus.equalsIgnoreCase(currentStatus)) {
            log.info("Idempotent status update for parcel {}: already in status {}", parcelId, currentStatus);
            return new CourierStatusUpdateResponse(
                parcel.id(),
                parcel.trackingNumber(),
                currentStatus,
                currentStatus,
                parcel.updatedAt() != null ? parcel.updatedAt() : Instant.now(),
                calculateAllowedNextStatuses(currentStatus)
            );
        }

        // 3. State machine validation
        validateStateTransition(currentStatus, targetStatus);

        // 4. Validate requirements for target state
        if ("FAILED".equalsIgnoreCase(targetStatus)) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failure reason is required when status is FAILED");
            }
        }

        DeliveryProofDto proofDto = null;
        if ("DELIVERED".equalsIgnoreCase(targetStatus)) {
            Optional<DeliveryProofEntity> proofOpt = deliveryProofRepository.findFirstByParcelIdOrderByUploadedAtDesc(parcelId);
            if (proofOpt.isEmpty() && (request.recipientName() == null || request.recipientName().isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof of delivery (photo or recipient name) is required to mark as DELIVERED");
            }
            if (proofOpt.isPresent()) {
                DeliveryProofEntity entity = proofOpt.get();
                proofDto = new DeliveryProofDto(entity.getRecipientName(), entity.getFilePath(), entity.getNotes());
            } else {
                proofDto = new DeliveryProofDto(request.recipientName(), null, request.reason());
            }
        }

        // 5. Delegate to M1's PATCH /internal/v1/parcels/{id}/status
        ParcelStatusUpdateRequest clientRequest = new ParcelStatusUpdateRequest(
            targetStatus,
            currentStatus,
            courierId,
            request.reason(),
            proofDto,
            request.idempotencyKey()
        );

        ParcelResponse updated = parcelServiceClient.updateStatus(parcelId, clientRequest);
        String newStatus = updated != null && updated.status() != null ? updated.status() : targetStatus;

        return new CourierStatusUpdateResponse(
            parcel.id(),
            parcel.trackingNumber(),
            currentStatus,
            newStatus,
            updated != null && updated.updatedAt() != null ? updated.updatedAt() : Instant.now(),
            calculateAllowedNextStatuses(newStatus)
        );
    }

    @Transactional
    public ProofUploadResponse uploadProof(
        String courierId,
        UUID parcelId,
        MultipartFile file,
        String recipientName,
        String notes
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload file is required");
        }

        if (file.getSize() > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds the 5MB limit");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read upload file bytes", e);
        }

        String mimeType = ContentTypeDetector.detectImageMimeType(bytes);
        if (mimeType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file content: only JPEG, PNG, and WEBP image files are allowed");
        }

        // Validate parcel existence and ownership
        ParcelResponse parcel = parcelServiceClient.getParcel(parcelId);
        if (parcel == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found with ID: " + parcelId);
        }
        validateCourierOwnership(parcel, courierId);

        // Store file to disk under configurable directory
        String extension = ContentTypeDetector.getExtensionForMimeType(mimeType);
        long timestamp = Instant.now().getEpochSecond();
        String filename = parcelId + "-" + timestamp + "." + extension;

        Path targetDir = Paths.get(storageDir);
        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(filename);
            Files.write(targetFile, bytes);
        } catch (IOException e) {
            log.warn("Failed to write proof photo to configured storageDir: {}, falling back to temp dir", storageDir, e);
            try {
                Path fallbackDir = Paths.get(System.getProperty("java.io.tmpdir"), "swiftdrop", "proofs");
                Files.createDirectories(fallbackDir);
                Path targetFile = fallbackDir.resolve(filename);
                Files.write(targetFile, bytes);
                targetDir = fallbackDir;
            } catch (IOException ex) {
                log.error("Failed to write proof photo to fallback temp dir as well", ex);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store proof file on disk", ex);
            }
        }

        UUID proofId = UUID.randomUUID();
        String proofUrl = "/api/v1/courier/proofs/" + proofId;

        DeliveryProofEntity proofEntity = DeliveryProofEntity.builder()
            .id(proofId)
            .parcelId(parcelId)
            .courierId(courierId)
            .proofType("PHOTO")
            .proofUrl(proofUrl)
            .recipientName(recipientName != null && !recipientName.isBlank() ? recipientName.trim() : parcel.recipientName())
            .filePath(targetDir.resolve(filename).toString())
            .fileSizeBytes(file.getSize())
            .mimeType(mimeType)
            .notes(notes)
            .uploadedAt(Instant.now())
            .build();

        deliveryProofRepository.save(proofEntity);

        return new ProofUploadResponse(
            proofId,
            parcelId,
            proofUrl,
            mimeType,
            file.getSize(),
            proofEntity.getUploadedAt()
        );
    }

    private void validateCourierOwnership(ParcelResponse parcel, String courierId) {
        if (parcel.courierId() == null || !parcel.courierId().equals(courierId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: courier is not assigned to parcel " + parcel.id());
        }
    }

    private void validateStateTransition(String current, String target) {
        List<String> allowed = calculateAllowedNextStatuses(current);
        if (!allowed.contains(target.toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Illegal status transition: cannot transition from " + current + " to " + target);
        }
    }

    public List<String> calculateAllowedNextStatuses(String currentStatus) {
        if (currentStatus == null) return List.of();
        return switch (currentStatus.toUpperCase(Locale.ROOT)) {
            case "CREATED" -> List.of("PICKED_UP");
            case "PICKED_UP" -> List.of("IN_TRANSIT");
            case "IN_TRANSIT" -> List.of("OUT_FOR_DELIVERY");
            case "OUT_FOR_DELIVERY" -> List.of("DELIVERED", "FAILED");
            default -> List.of();
        };
    }

    private String buildGoogleMapsUrl(AddressDto address) {
        if (address == null) return "https://www.google.com/maps";
        String query = address.toSingleLineString();
        return "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}
