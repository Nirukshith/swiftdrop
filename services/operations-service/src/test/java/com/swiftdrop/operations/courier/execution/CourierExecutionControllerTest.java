package com.swiftdrop.operations.courier.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.operations.courier.execution.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CourierExecutionController.class)
@AutoConfigureMockMvc(addFilters = false)
class CourierExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CourierExecutionService courierExecutionService;

    private static final UUID PARCEL_ID = UUID.fromString("7e9b3a24-c189-4b68-b7c1-fa9e6a0d2f11");
    private static final String COURIER_ID = "courier-42";

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("GET /api/v1/courier/deliveries returns 200 with delivery list")
    void getTodaysDeliveriesShouldReturnOk() throws Exception {
        CourierDeliveriesResponse response = new CourierDeliveriesResponse(
            "2026-10-28",
            1,
            1,
            0,
            List.of(new CourierDeliveryCard(
                PARCEL_ID,
                "SD-20261028-001",
                "OUT_FOR_DELIVERY",
                "Sunil Silva",
                "+94771234567",
                "45 Galle Rd",
                "12 Kandy Rd",
                "https://maps.google.com",
                2.5,
                0,
                1
            ))
        );

        when(courierExecutionService.getTodaysDeliveries(eq(COURIER_ID), any(), any()))
            .thenReturn(response);

        mockMvc.perform(get("/api/v1/courier/deliveries")
                .param("date", "2026-10-28"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDeliveries").value(1))
            .andExpect(jsonPath("$.deliveries[0].trackingNumber").value("SD-20261028-001"));
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("PATCH /api/v1/courier/deliveries/{id}/status returns 200 on successful transition")
    void updateDeliveryStatusShouldReturnOk() throws Exception {
        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest("DELIVERED", null, "Sunil Silva", "idem-1");
        CourierStatusUpdateResponse response = new CourierStatusUpdateResponse(
            PARCEL_ID,
            "SD-20261028-001",
            "OUT_FOR_DELIVERY",
            "DELIVERED",
            Instant.now(),
            List.of()
        );

        when(courierExecutionService.updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any()))
            .thenReturn(response);

        mockMvc.perform(patch("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStatus").value("DELIVERED"));
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("PATCH /api/v1/courier/deliveries/{id}/status returns 403 on courier ownership mismatch")
    void updateDeliveryStatusForbiddenForMismatchedCourier() throws Exception {
        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest("DELIVERED", null, "Sunil Silva", null);

        when(courierExecutionService.updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: courier is not assigned to this parcel"));

        mockMvc.perform(patch("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("POST /api/v1/courier/deliveries/{id}/proof returns 201 on valid upload")
    void uploadProofShouldReturnCreated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "proof.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        ProofUploadResponse response = new ProofUploadResponse(
            UUID.randomUUID(),
            PARCEL_ID,
            "/api/v1/courier/proofs/123",
            "image/jpeg",
            4L,
            Instant.now()
        );

        when(courierExecutionService.uploadProof(eq(COURIER_ID), eq(PARCEL_ID), any(), any(), any()))
            .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/proof", PARCEL_ID)
                .file(file)
                .param("recipientName", "Sunil Silva")
                .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
            .andExpect(jsonPath("$.proofUrl").value("/api/v1/courier/proofs/123"));
    }
}
