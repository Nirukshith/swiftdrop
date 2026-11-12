package com.swiftdrop.operations.courier.execution;

import com.swiftdrop.operations.courier.execution.client.*;
import com.swiftdrop.operations.courier.execution.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourierExecutionServiceTest {

    @Mock
    private ParcelServiceClient parcelServiceClient;

    @Mock
    private DeliveryProofRepository deliveryProofRepository;

    @Mock
    private DeliveryProofAuditService deliveryProofAuditService;

    @TempDir
    Path tempDir;

    private CourierExecutionService courierExecutionService;

    private static final String COURIER_A = "courier-uuid-aaa";
    private static final String COURIER_B = "courier-uuid-bbb";
    private static final UUID PARCEL_ID = UUID.fromString("7e9b3a24-c189-4b68-b7c1-fa9e6a0d2f11");

    @BeforeEach
    void setUp() {
        courierExecutionService = new CourierExecutionService(
            parcelServiceClient,
            deliveryProofRepository,
            deliveryProofAuditService,
            tempDir.toString(),
            5 * 1024 * 1024 // 5 MB
        );
    }

    private ParcelResponse createMockParcel(String courierId, String status) {
        AddressDto pickup = new AddressDto("45 Galle Rd", null, "Colombo 03", "Colombo", "00300");
        AddressDto dropoff = new AddressDto("12 Kandy Rd", null, "Kiribathgoda", "Gampaha", "11600");
        return new ParcelResponse(
            PARCEL_ID,
            "SD-20261028-001",
            status,
            "cust-1",
            null,
            courierId,
            pickup,
            dropoff,
            2.5,
            "MEDIUM",
            750.0,
            0,
            null,
            "Sunil Silva",
            "+94771234567",
            "Handle with care",
            null,
            1L,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    @DisplayName("Ownership check: Courier B cannot update Courier A's delivery")
    void ownershipCheckMustRejectMismatchedCourier() {
        ParcelResponse parcelOwnedByA = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcelOwnedByA);

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest("DELIVERED", null, "Sunil Silva", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.updateDeliveryStatus(COURIER_B, PARCEL_ID, request)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("Access denied");
        verify(parcelServiceClient, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("Ownership check: Courier B cannot view Courier A's delivery detail")
    void ownershipCheckMustRejectDetailViewForMismatchedCourier() {
        ParcelResponse parcelOwnedByA = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcelOwnedByA);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.getDeliveryDetail(COURIER_B, PARCEL_ID)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Safe retries: Repeated status request returns 200 without duplicate client calls")
    void repeatedStatusRequestMustBeIdempotent() {
        ParcelResponse parcelAlreadyOut = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcelAlreadyOut);

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest("OUT_FOR_DELIVERY", null, null, "retry-key-1");

        CourierStatusUpdateResponse response = courierExecutionService.updateDeliveryStatus(COURIER_A, PARCEL_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.currentStatus()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(response.previousStatus()).isEqualTo("OUT_FOR_DELIVERY");
        verify(parcelServiceClient, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("Illegal transition leap from CREATED to DELIVERED must throw 409 Conflict")
    void illegalTransitionLeapMustThrowConflict() {
        ParcelResponse createdParcel = createMockParcel(COURIER_A, "CREATED");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(createdParcel);

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest("DELIVERED", null, "Sunil Silva", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.updateDeliveryStatus(COURIER_A, PARCEL_ID, request)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getReason()).contains("Illegal status transition");
    }

    @Test
    @DisplayName("Marking FAILED without reason must throw 400 Bad Request")
    void markingFailedWithoutReasonMustThrowBadRequest() {
        ParcelResponse outForDeliveryParcel = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(outForDeliveryParcel);

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest("FAILED", "", null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.updateDeliveryStatus(COURIER_A, PARCEL_ID, request)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("Failure reason is required");
    }

    @Test
    @DisplayName("Proof upload: Rejects invalid file content (magic bytes mismatch) with 400 Bad Request")
    void proofUploadMustRejectInvalidFileTypeByContent() {
        byte[] fakeTextBytes = "This is a text file masquerading as a jpeg".getBytes();
        MockMultipartFile fakeFile = new MockMultipartFile(
            "file", "proof.jpg", "image/jpeg", fakeTextBytes
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, fakeFile, "Sunil Silva", "Delivered")
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("Invalid file content");
        verify(deliveryProofRepository, never()).save(any());
    }

    @Test
    @DisplayName("Proof upload: Rejects files exceeding 5MB size limit with 413 Payload Too Large")
    void proofUploadMustEnforceSizeLimit() {
        // Create 6MB payload with valid JPEG header
        byte[] largeBytes = new byte[6 * 1024 * 1024];
        largeBytes[0] = (byte) 0xFF;
        largeBytes[1] = (byte) 0xD8;
        largeBytes[2] = (byte) 0xFF;

        MockMultipartFile largeFile = new MockMultipartFile(
            "file", "large-proof.jpg", "image/jpeg", largeBytes
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, largeFile, "Sunil", null)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(ex.getReason()).contains("5MB limit");
    }

    @Test
    @DisplayName("Proof upload: Valid JPEG magic bytes succeeds and persists metadata")
    void proofUploadValidJpegSucceeds() {
        byte[] validJpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
        MockMultipartFile validFile = new MockMultipartFile(
            "file", "doorstep.jpg", "image/jpeg", validJpegBytes
        );

        ParcelResponse parcel = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcel);
        when(deliveryProofRepository.save(any(DeliveryProofEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProofUploadResponse response = courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, validFile, "Sunil Silva", "Handed over");

        assertThat(response).isNotNull();
        assertThat(response.parcelId()).isEqualTo(PARCEL_ID);
        assertThat(response.mimeType()).isEqualTo("image/jpeg");
        assertThat(response.proofUrl()).startsWith("/api/v1/courier/proofs/");
        verify(deliveryProofRepository).save(any(DeliveryProofEntity.class));
    }

    @Test
    @DisplayName("Valid status transition from OUT_FOR_DELIVERY to DELIVERED with recorded proof succeeds")
    void transitionToDeliveredWithProofSucceeds() {
        ParcelResponse outParcel = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        ParcelResponse deliveredParcel = createMockParcel(COURIER_A, "DELIVERED");

        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(outParcel);
        when(deliveryProofRepository.findFirstByParcelIdOrderByUploadedAtDesc(PARCEL_ID)).thenReturn(Optional.of(
            DeliveryProofEntity.builder()
                .id(UUID.randomUUID())
                .parcelId(PARCEL_ID)
                .courierId(COURIER_A)
                .recipientName("Sunil Silva")
                .filePath("/data/proofs/proof.jpg")
                .proofType("PHOTO")
                .proofUrl("/api/v1/courier/proofs/123")
                .mimeType("image/jpeg")
                .fileSizeBytes(1024L)
                .uploadedAt(Instant.now())
                .build()
        ));
        when(parcelServiceClient.updateStatus(eq(PARCEL_ID), any())).thenReturn(deliveredParcel);

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest("DELIVERED", null, "Sunil Silva", "idemp-key-99");
        CourierStatusUpdateResponse response = courierExecutionService.updateDeliveryStatus(COURIER_A, PARCEL_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.previousStatus()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(response.currentStatus()).isEqualTo("DELIVERED");
        verify(parcelServiceClient).updateStatus(eq(PARCEL_ID), any());
    }

    @Test
    @DisplayName("Proof upload cleans up written file if database save fails")
    void proofUploadCleansUpFileIfDatabaseSaveFails() {
        byte[] validJpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
        MockMultipartFile validFile = new MockMultipartFile(
            "file", "doorstep.jpg", "image/jpeg", validJpegBytes
        );

        ParcelResponse parcel = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcel);
        when(deliveryProofRepository.save(any(DeliveryProofEntity.class)))
            .thenThrow(new RuntimeException("Simulated database failure"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, validFile, "Sunil Silva", "Handed over")
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // Verify no leftover files in tempDir
        try (var stream = java.nio.file.Files.list(tempDir)) {
            assertThat(stream.count()).isEqualTo(0);
        } catch (java.io.IOException e) {
            org.junit.jupiter.api.Assertions.fail(e);
        }
    }

    @Test
    @DisplayName("Today's deliveries defaults to Asia/Colombo date when date parameter is omitted")
    void getTodaysDeliveriesShouldDefaultToAsiaColomboDate() {
        String expectedDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")).toString();
        ParcelPageResponse pageResponse = new ParcelPageResponse(List.of(), 0, 0, 0, 20);
        when(parcelServiceClient.getParcelsByCourierAndDate(eq(COURIER_A), eq(expectedDate)))
            .thenReturn(pageResponse);

        CourierDeliveriesResponse response = courierExecutionService.getTodaysDeliveries(COURIER_A, null, null);

        assertThat(response).isNotNull();
        assertThat(response.date()).isEqualTo(expectedDate);
        verify(parcelServiceClient).getParcelsByCourierAndDate(COURIER_A, expectedDate);
    }

    @Test
    @DisplayName("Proof upload: Empty file must be rejected with 400 Bad Request")
    void proofUploadEmptyFileMustBeRejected() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, emptyFile, "Sunil", null)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("Upload file is required");
    }

    @Test
    @DisplayName("Proof upload: Exact 5 MB file size boundary is accepted when content is valid")
    void proofUploadExact5MbFileIsAccepted() {
        int exact5Mb = 5 * 1024 * 1024;
        byte[] bytes = new byte[exact5Mb];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;

        MockMultipartFile file5Mb = new MockMultipartFile("file", "5mb.jpg", "image/jpeg", bytes);

        ParcelResponse parcel = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcel);
        when(deliveryProofRepository.save(any(DeliveryProofEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProofUploadResponse response = courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, file5Mb, "Sunil", null);

        assertThat(response).isNotNull();
        assertThat(response.fileSizeBytes()).isEqualTo(exact5Mb);
        assertThat(response.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("Proof upload: Valid PNG file is accepted and detected")
    void proofUploadValidPngSucceeds() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "proof.png", "image/png", pngBytes);

        ParcelResponse parcel = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcel);
        when(deliveryProofRepository.save(any(DeliveryProofEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProofUploadResponse response = courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, file, "Sunil", null);

        assertThat(response).isNotNull();
        assertThat(response.mimeType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Proof upload: Valid WEBP file is accepted and detected")
    void proofUploadValidWebpSucceeds() {
        byte[] webpBytes = new byte[]{
            'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0
        };
        MockMultipartFile file = new MockMultipartFile("file", "proof.webp", "image/webp", webpBytes);

        ParcelResponse parcel = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcel);
        when(deliveryProofRepository.save(any(DeliveryProofEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProofUploadResponse response = courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, file, "Sunil", null);

        assertThat(response).isNotNull();
        assertThat(response.mimeType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("Proof upload: Unassigned parcel must be rejected with 403 Forbidden")
    void proofUploadUnassignedParcelMustBeRejected() {
        byte[] validJpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile("file", "proof.jpg", "image/jpeg", validJpegBytes);

        ParcelResponse unassignedParcel = createMockParcel(null, "CREATED");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(unassignedParcel);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, file, "Sunil", null)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("is not assigned to any courier");
        verify(deliveryProofRepository, never()).save(any());
    }

    @Test
    @DisplayName("Proof upload: Nonexistent parcel must return 404 Not Found")
    void proofUploadNonexistentParcelMustReturnNotFound() {
        byte[] validJpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile("file", "proof.jpg", "image/jpeg", validJpegBytes);

        when(parcelServiceClient.getParcel(PARCEL_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, file, "Sunil", null)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(deliveryProofRepository, never()).save(any());
    }

    @Test
    @DisplayName("Proof upload: Successfully records DELIVERY_PROOF_UPLOADED audit event")
    void proofUploadRecordsAuditOnSuccess() {
        byte[] validJpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile("file", "proof.jpg", "image/jpeg", validJpegBytes);

        ParcelResponse parcel = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcel);
        when(deliveryProofRepository.save(any(DeliveryProofEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProofUploadResponse response = courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, file, "Sunil", "Notes");

        assertThat(response).isNotNull();
        verify(deliveryProofAuditService).record(
            eq(COURIER_A),
            eq("COURIER"),
            eq("DELIVERY_PROOF_UPLOADED"),
            eq("DELIVERY_PROOF"),
            eq(response.proofId().toString()),
            any()
        );
    }

    @Test
    @DisplayName("Proof upload: Records DELIVERY_PROOF_REJECTED audit event when validation fails")
    void proofUploadRecordsAuditOnRejection() {
        byte[] corruptedBytes = new byte[]{0x00, 0x01, 0x02, 0x03};
        MockMultipartFile file = new MockMultipartFile("file", "bad.jpg", "image/jpeg", corruptedBytes);

        assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.uploadProof(COURIER_A, PARCEL_ID, file, "Sunil", null)
        );

        verify(deliveryProofAuditService).record(
            eq(COURIER_A),
            eq("COURIER"),
            eq("DELIVERY_PROOF_REJECTED"),
            eq("PARCEL"),
            eq(PARCEL_ID.toString()),
            any()
        );
    }

    @Test
    @DisplayName("Status update: Successfully records DELIVERY_STATUS_UPDATED audit event")
    void statusUpdateRecordsAuditOnSuccess() {
        ParcelResponse parcel = createMockParcel(COURIER_A, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcel);
        when(parcelServiceClient.updateStatus(eq(PARCEL_ID), any())).thenReturn(
            new ParcelResponse(PARCEL_ID, "SD-001", "DELIVERED", "cust-1", null, COURIER_A, null, null, 1.0, "SMALL", 100.0, 0, null, "Sunil", null, null, null, 1L, Instant.now(), Instant.now())
        );

        CourierStatusUpdateRequest req = new CourierStatusUpdateRequest("DELIVERED", null, "Sunil", "idem-audit-1");
        CourierStatusUpdateResponse resp = courierExecutionService.updateDeliveryStatus(COURIER_A, PARCEL_ID, req);

        assertThat(resp).isNotNull();
        verify(deliveryProofAuditService).record(
            eq(COURIER_A),
            eq("COURIER"),
            eq("DELIVERY_STATUS_UPDATED"),
            eq("PARCEL"),
            eq(PARCEL_ID.toString()),
            any()
        );
    }

    @Test
    @DisplayName("Status update: Records audit rejection on unauthorized courier access")
    void statusUpdateRecordsAuditOnUnauthorizedAccess() {
        ParcelResponse parcel = createMockParcel(COURIER_B, "OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(parcel);

        CourierStatusUpdateRequest req = new CourierStatusUpdateRequest("DELIVERED", null, "Sunil", null);
        assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.updateDeliveryStatus(COURIER_A, PARCEL_ID, req)
        );

        verify(deliveryProofAuditService).record(
            eq(COURIER_A),
            eq("COURIER"),
            eq("DELIVERY_PROOF_REJECTED"),
            eq("PARCEL"),
            eq(PARCEL_ID.toString()),
            any()
        );
    }
}
