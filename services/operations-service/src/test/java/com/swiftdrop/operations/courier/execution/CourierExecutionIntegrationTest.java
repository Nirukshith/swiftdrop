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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class CourierExecutionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CourierExecutionService courierExecutionService;

    @Autowired
    private DeliveryProofRepository deliveryProofRepository;

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
    @DisplayName("End-to-end: upload proof into PostgreSQL, verify repository record, and complete delivery")
    void testUploadProofAndCompleteDelivery() {
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
    }
}
