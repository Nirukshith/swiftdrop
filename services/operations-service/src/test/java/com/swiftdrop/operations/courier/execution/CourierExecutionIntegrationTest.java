package com.swiftdrop.operations.courier.execution;

import com.swiftdrop.operations.courier.execution.client.*;
import com.swiftdrop.operations.courier.execution.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.swiftdrop.operations.courier.profile.CourierProfileOperations;
import com.swiftdrop.operations.OperationsKafkaIntegrationTest;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "swiftdrop.proofs.storage-dir=${java.io.tmpdir}/swiftdrop/proofs-it"
})
@Testcontainers
class CourierExecutionIntegrationTest extends OperationsKafkaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CourierExecutionService courierExecutionService;

    @Autowired
    private DeliveryProofRepository deliveryProofRepository;

    @Autowired
    private DeliveryProofAuditRepository deliveryProofAuditRepository;

    @Autowired
    private DeliveryProofAuditService deliveryProofAuditService;

    @Autowired
    private CourierProfileOperations courierProfileOperations;

    @MockBean
    private ParcelServiceClient parcelServiceClient;

    private static final String COURIER_ID = "courier-it-001";
    private static final UUID PARCEL_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private ParcelResponse mockParcel(String status) {
        return new ParcelResponse(
            PARCEL_ID,
            "SD-20261028-IT",
            status,
            "cust-it",
            null,
            COURIER_ID,
            new AddressDto("10 Pickup Rd", null, "Colombo", "Colombo", "00100"),
            new AddressDto("20 Dropoff Rd", null, "Kandy", "Kandy", "20000"),
            1.5,
            "SMALL",
            500.0,
            0,
            null,
            "Nimal Perera",
            "+94711112222",
            "Doorbell",
            null,
            2L,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("End-to-end: upload proof into PostgreSQL, verify repository record, complete delivery, and retrieve proof")
    void testUploadProofAndCompleteDelivery() throws Exception {
        ParcelResponse outParcel = mockParcel("OUT_FOR_DELIVERY");
        ParcelResponse deliveredParcel = mockParcel("DELIVERED");

        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(outParcel);
        when(parcelServiceClient.updateStatus(eq(PARCEL_ID), any())).thenReturn(deliveredParcel);

        // 1. Upload proof
        byte[] validJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
        MockMultipartFile file = new MockMultipartFile("file", "delivery.jpg", "image/jpeg", validJpeg);

        ProofUploadResponse proofResp = courierExecutionService.uploadProof(
            COURIER_ID, PARCEL_ID, file, "Nimal Perera", "Delivered to security"
        );

        assertThat(proofResp).isNotNull();
        assertThat(proofResp.proofId()).isNotNull();

        // 2. Verify record in PostgreSQL database
        Optional<DeliveryProofEntity> inDb = deliveryProofRepository.findById(proofResp.proofId());
        assertThat(inDb).isPresent();
        assertThat(inDb.get().getParcelId()).isEqualTo(PARCEL_ID);
        assertThat(inDb.get().getCourierId()).isEqualTo(COURIER_ID);
        assertThat(inDb.get().getMimeType()).isEqualTo("image/jpeg");

        // 3. Mark delivered
        CourierStatusUpdateRequest updateReq = new CourierStatusUpdateRequest("DELIVERED", null, "Nimal Perera", "idem-it-1");
        CourierStatusUpdateResponse statusResp = courierExecutionService.updateDeliveryStatus(COURIER_ID, PARCEL_ID, updateReq);

        assertThat(statusResp).isNotNull();
        assertThat(statusResp.currentStatus()).isEqualTo("DELIVERED");
        assertThat(statusResp.previousStatus()).isEqualTo("OUT_FOR_DELIVERY");

        // 4. Retrieve the uploaded proof through authorized operations
        var proofResource = courierProfileOperations.getProof(proofResp.proofId().toString());
        assertThat(proofResource).isNotNull();
        assertThat(proofResource.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(proofResource.getBody()).isNotNull();
        assertThat(proofResource.getBody().contentLength()).isEqualTo(validJpeg.length);
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("Delivery proof upload, SHA-256 integrity, audit log, and restart persistence across service re-instantiation")
    void testDeliveryProofAndAuditRestartPersistence() throws Exception {
        ParcelResponse outParcel = mockParcel("OUT_FOR_DELIVERY");
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(outParcel);

        byte[] originalBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x11, 0x22, 0x33, 0x44};
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] expectedHash = digest.digest(originalBytes);

        MockMultipartFile file = new MockMultipartFile("file", "proof-restart.jpg", "image/jpeg", originalBytes);

        // 1. Upload proof
        ProofUploadResponse proofResp = courierExecutionService.uploadProof(
            COURIER_ID, PARCEL_ID, file, "Sunil Shantha", "Signed on receipt"
        );
        UUID proofId = proofResp.proofId();
        assertThat(proofId).isNotNull();

        // 2. Verify metadata stored in PostgreSQL
        Optional<DeliveryProofEntity> proofEntityOpt = deliveryProofRepository.findById(proofId);
        assertThat(proofEntityOpt).isPresent();
        DeliveryProofEntity proofEntity = proofEntityOpt.get();
        assertThat(proofEntity.getParcelId()).isEqualTo(PARCEL_ID);
        assertThat(proofEntity.getCourierId()).isEqualTo(COURIER_ID);
        assertThat(proofEntity.getFilePath()).isNotBlank();
        Path filePath = Path.of(proofEntity.getFilePath());
        assertThat(Files.exists(filePath)).isTrue();
        byte[] onDiskBytes = Files.readAllBytes(filePath);
        assertThat(digest.digest(onDiskBytes)).isEqualTo(expectedHash);

        // 3. Verify audit record stored in ops_db.audit_log
        List<DeliveryProofAuditLogEntity> auditLogs = deliveryProofAuditRepository.findAll();
        assertThat(auditLogs).anyMatch(log ->
            "DELIVERY_PROOF_UPLOADED".equals(log.getAction()) &&
            proofId.toString().equals(log.getTargetId()) &&
            COURIER_ID.equals(log.getActorId())
        );

        // 4. Simulate service restart by retrieving via courierProfileOperations directly
        var retrieved = courierProfileOperations.getProof(proofId.toString());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getStatusCode().is2xxSuccessful()).isTrue();
        Resource body = retrieved.getBody();
        assertThat(body).isNotNull();
        byte[] retrievedBytes = body.getInputStream().readAllBytes();
        assertThat(digest.digest(retrievedBytes)).isEqualTo(expectedHash);

        // 5. Verify metadata and audit log remain queryable after simulated restart
        Optional<DeliveryProofEntity> reloadedProof = deliveryProofRepository.findById(proofId);
        assertThat(reloadedProof).isPresent();
        assertThat(reloadedProof.get().getFileSizeBytes()).isEqualTo(originalBytes.length);

        List<DeliveryProofAuditLogEntity> reloadedAuditLogs = deliveryProofAuditRepository.findAll();
        assertThat(reloadedAuditLogs).anyMatch(log ->
            "DELIVERY_PROOF_UPLOADED".equals(log.getAction()) &&
            proofId.toString().equals(log.getTargetId())
        );
    }

    @Test
    @WithMockUser(username = "other-courier-999", roles = {"COURIER"})
    @DisplayName("Unauthorized proof retrieval is rejected with 403 Forbidden")
    void testUnauthorizedProofRetrievalIsRejected() {
        UUID proofId = UUID.randomUUID();
        DeliveryProofEntity proof = DeliveryProofEntity.builder()
            .id(proofId)
            .parcelId(PARCEL_ID)
            .courierId(COURIER_ID)
            .proofType("PHOTO")
            .proofUrl("/api/v1/courier/proofs/" + proofId)
            .recipientName("Nimal Perera")
            .filePath(System.getProperty("java.io.tmpdir") + "/dummy.jpg")
            .mimeType("image/jpeg")
            .fileSizeBytes(100L)
            .uploadedAt(Instant.now())
            .build();
        deliveryProofRepository.save(proof);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierProfileOperations.getProof(proofId.toString())
        );
        assertThat(ex.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("Missing proof retrieval returns 404 Not Found")
    void testMissingProofRetrievalReturnsNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierProfileOperations.getProof(UUID.randomUUID().toString())
        );
        assertThat(ex.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @WithMockUser(username = "dispatcher-01", roles = {"DISPATCHER"})
    @DisplayName("Authorized Dispatcher can retrieve delivery proof")
    void testDispatcherCanRetrieveProof() throws Exception {
        byte[] validJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "disp.jpg", "image/jpeg", validJpeg);
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(mockParcel("OUT_FOR_DELIVERY"));

        ProofUploadResponse proofResp = courierExecutionService.uploadProof(
            COURIER_ID, PARCEL_ID, file, "Nimal Perera", "Notes"
        );

        var proofResource = courierProfileOperations.getProof(proofResp.proofId().toString());
        assertThat(proofResource).isNotNull();
        assertThat(proofResource.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("Failed delivery workflow: transition to FAILED requires reason and persists audit record")
    void testFailedDeliveryWorkflowWithReasonAndAudit() {
        ParcelResponse outParcel = mockParcel("OUT_FOR_DELIVERY");
        ParcelResponse failedParcel = mockParcel("FAILED");

        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(outParcel);
        when(parcelServiceClient.updateStatus(eq(PARCEL_ID), any())).thenReturn(failedParcel);

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest(
            "FAILED", "Customer refused acceptance", null, "idem-fail-1"
        );

        CourierStatusUpdateResponse response = courierExecutionService.updateDeliveryStatus(
            COURIER_ID, PARCEL_ID, request
        );

        assertThat(response).isNotNull();
        assertThat(response.currentStatus()).isEqualTo("FAILED");

        List<DeliveryProofAuditLogEntity> audits = deliveryProofAuditRepository.findAll();
        assertThat(audits).anyMatch(log ->
            "DELIVERY_STATUS_UPDATED".equals(log.getAction()) &&
            PARCEL_ID.toString().equals(log.getTargetId()) &&
            log.getDetails() != null &&
            "FAILED".equals(log.getDetails().get("currentStatus")) &&
            "Customer refused acceptance".equals(log.getDetails().get("reason"))
        );
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("Transition to FAILED without reason throws 400 Bad Request")
    void testTransitionToFailedWithoutReasonThrowsBadRequest() {
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(mockParcel("OUT_FOR_DELIVERY"));

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest(
            "FAILED", "  ", null, "idem-fail-2"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.updateDeliveryStatus(COURIER_ID, PARCEL_ID, request)
        );
        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        assertThat(ex.getReason()).contains("Failure reason is required");
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("Transition to DELIVERED without proof or recipient name throws 400 Bad Request")
    void testTransitionToDeliveredWithoutProofOrRecipientNameThrowsBadRequest() {
        UUID freshParcelId = UUID.randomUUID();
        ParcelResponse parcel = new ParcelResponse(
            freshParcelId, "SD-NOPROOF-01", "OUT_FOR_DELIVERY", "cust-1", null, COURIER_ID,
            null, null, 1.0, "SMALL", 300.0, 0, null, "John Doe", "+94770000000", null, null, 1L, Instant.now(), Instant.now()
        );
        when(parcelServiceClient.getParcel(freshParcelId)).thenReturn(parcel);

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest(
            "DELIVERED", null, null, "idem-deliv-none"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.updateDeliveryStatus(COURIER_ID, freshParcelId, request)
        );
        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        assertThat(ex.getReason()).contains("Proof of delivery (photo or recipient name) is required");
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("Proof upload with corrupted/spoofed magic bytes is rejected with 400 and logged to audit")
    void testProofUploadWithInvalidMagicBytesThrowsBadRequestAndAuditsRejection() {
        byte[] fakeBytes = "NOT_A_VALID_IMAGE_HEADER_MZ".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "malicious.jpg", "image/jpeg", fakeBytes);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.uploadProof(COURIER_ID, PARCEL_ID, file, "Recipient", "Notes")
        );
        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        assertThat(ex.getReason()).contains("Invalid file content");

        List<DeliveryProofAuditLogEntity> audits = deliveryProofAuditRepository.findAll();
        assertThat(audits).anyMatch(log ->
            "DELIVERY_PROOF_REJECTED".equals(log.getAction()) &&
            PARCEL_ID.toString().equals(log.getTargetId()) &&
            log.getDetails() != null &&
            "INVALID_FILE_TYPE".equals(log.getDetails().get("reason"))
        );
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("Proof upload exceeding 5MB size limit is rejected with 413 and logged to audit")
    void testProofUploadExceeding5MbThrowsPayloadTooLargeAndAuditsRejection() {
        byte[] oversizedBytes = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "large.jpg", "image/jpeg", oversizedBytes);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.uploadProof(COURIER_ID, PARCEL_ID, file, "Recipient", "Notes")
        );
        assertThat(ex.getStatusCode().value()).isEqualTo(413);
        assertThat(ex.getReason()).contains("exceeds the 5MB limit");

        List<DeliveryProofAuditLogEntity> audits = deliveryProofAuditRepository.findAll();
        assertThat(audits).anyMatch(log ->
            "DELIVERY_PROOF_REJECTED".equals(log.getAction()) &&
            PARCEL_ID.toString().equals(log.getTargetId()) &&
            log.getDetails() != null &&
            "FILE_SIZE_EXCEEDED".equals(log.getDetails().get("reason"))
        );
    }

    @Test
    @WithMockUser(username = "unauthorized-courier-88", roles = {"COURIER"})
    @DisplayName("Status update by mismatched courier throws 403 and audits unauthorized attempt")
    void testStatusUpdateByMismatchedCourierAuditsUnauthorizedAttempt() {
        when(parcelServiceClient.getParcel(PARCEL_ID)).thenReturn(mockParcel("OUT_FOR_DELIVERY"));

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest(
            "DELIVERED", null, "Recipient", "idem-unauth"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            courierExecutionService.updateDeliveryStatus("unauthorized-courier-88", PARCEL_ID, request)
        );
        assertThat(ex.getStatusCode().value()).isEqualTo(403);

        List<DeliveryProofAuditLogEntity> audits = deliveryProofAuditRepository.findAll();
        assertThat(audits).anyMatch(log ->
            "DELIVERY_PROOF_REJECTED".equals(log.getAction()) &&
            PARCEL_ID.toString().equals(log.getTargetId()) &&
            "unauthorized-courier-88".equals(log.getActorId()) &&
            log.getDetails() != null &&
            "UNAUTHORIZED_COURIER_ACCESS_ATTEMPT".equals(log.getDetails().get("reason"))
        );
    }
}
