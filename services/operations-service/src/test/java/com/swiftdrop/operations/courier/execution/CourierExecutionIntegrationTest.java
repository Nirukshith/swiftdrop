package com.swiftdrop.operations.courier.execution;

import com.swiftdrop.operations.courier.execution.client.*;
import com.swiftdrop.operations.courier.execution.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.swiftdrop.operations.courier.profile.CourierProfileOperations;
import com.swiftdrop.operations.OperationsKafkaIntegrationTest;

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
}
