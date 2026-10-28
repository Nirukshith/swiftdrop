package com.swiftdrop.operations.courier.profile;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.operations.courier.execution.DeliveryProofEntity;
import com.swiftdrop.operations.courier.execution.DeliveryProofRepository;
import com.swiftdrop.operations.courier.execution.client.AddressDto;
import com.swiftdrop.operations.courier.execution.client.ParcelResponse;
import com.swiftdrop.operations.courier.execution.client.ParcelServiceClient;
import com.swiftdrop.operations.courier.execution.client.ParcelStatusUpdateRequest;
import com.swiftdrop.operations.courier.execution.dto.CourierDeliveriesResponse;
import com.swiftdrop.operations.courier.execution.dto.CourierDeliveryCard;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
public class CourierProfileOperations {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");
    private final CourierProfileService profiles;
    private final ParcelServiceClient parcels;
    private final DeliveryProofRepository proofs;

    public CourierProfileOperations(CourierProfileService profiles, ParcelServiceClient parcels, DeliveryProofRepository proofs) {
        this.profiles = profiles;
        this.parcels = parcels;
        this.proofs = proofs;
    }

    @Transactional(readOnly = true)
    public CourierProfileResponse getProfile(String courierId) {
        CourierSummary profile = profiles.findCourierById(courierId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Courier profile not found"));
        return new CourierProfileResponse(profile.courierId(), profile.name(), profile.phoneNumber(), profile.zone(), profile.available(), profile.vehicleType());
    }

    @Transactional
    public CourierProfileResponse setAvailability(String courierId, boolean available) {
        if (profiles.findCourierById(courierId).isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Courier profile not found");
        }
        profiles.updateAvailability(courierId, available);
        return getProfile(courierId);
    }

    public void failDelivery(String courierId, UUID parcelId, FailedDeliveryRequest request) {
        try {
            request.validate();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage());
        }
        ParcelResponse parcel = parcels.getParcel(parcelId);
        if (parcel == null) throw new ResponseStatusException(NOT_FOUND, "Parcel not found");
        if (!courierId.equals(parcel.courierId())) {
            throw new ResponseStatusException(FORBIDDEN, "Courier is not assigned to this parcel");
        }
        parcels.updateStatus(parcelId, new ParcelStatusUpdateRequest(
            "FAILED", parcel.status(), courierId, request.reasonCode().name(), null, UUID.randomUUID().toString()));
    }

    public CourierDeliveriesResponse getSummary(String courierId, LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? LocalDate.now(BUSINESS_ZONE) : requestedDate;
        var response = parcels.getParcelsByCourierAndDate(courierId, date.toString());
        List<ParcelResponse> items = response == null || response.content() == null ? List.of() : response.content();
        int pending = 0;
        int completed = 0;
        int order = 1;
        List<CourierDeliveryCard> cards = new java.util.ArrayList<>();
        for (ParcelResponse parcel : items) {
            boolean done = isCompleted(parcel.status());
            if (done) completed++; else pending++;
            AddressDto dropoff = parcel.dropoffAddress();
            cards.add(new CourierDeliveryCard(parcel.id(), parcel.trackingNumber(), parcel.status(), parcel.recipientName(),
                parcel.recipientPhone(), address(parcel.pickupAddress()), address(dropoff), mapsUrl(dropoff), parcel.weightKg(),
                parcel.attemptCount() == null ? 0 : parcel.attemptCount(), order++));
        }
        return new CourierDeliveriesResponse(date.toString(), items.size(), pending, completed, cards);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> getProof(String proofId) {
        UUID id;
        try { id = UUID.fromString(proofId); } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid proof id");
        }
        DeliveryProofEntity proof = proofs.findById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Proof not found"));
        CurrentUser user = CurrentUser.require();
        if (!user.isDispatcher() && !user.isAdmin() && !proof.getCourierId().equals(user.userId())) {
            throw new ResponseStatusException(FORBIDDEN, "Proof is not visible to this courier");
        }
        Path path = Path.of(proof.getFilePath()).normalize();
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(NOT_FOUND, "Proof file not found");
        MediaType mediaType = MediaType.parseMediaType(proof.getMimeType());
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(mediaType.toString().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(NOT_FOUND, "Unsupported proof media type");
        }
        return ResponseEntity.ok().contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .body(new FileSystemResource(path));
    }

    private boolean isCompleted(String status) {
        return status != null && List.of("DELIVERED", "FAILED", "CANCELLED").contains(status.toUpperCase(Locale.ROOT));
    }

    private String address(AddressDto address) { return address == null ? "" : address.toSingleLineString(); }
    private String mapsUrl(AddressDto address) { return "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(address(address), java.nio.charset.StandardCharsets.UTF_8); }
}
