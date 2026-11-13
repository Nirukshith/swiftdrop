package com.swiftdrop.operations.courier.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.operations.courier.execution.dto.*;
import com.swiftdrop.operations.courier.profile.CourierProfileController;
import com.swiftdrop.operations.courier.profile.CourierProfileOperations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CourierExecutionController.class, CourierProfileController.class})
@Import(CourierSecurityConfig.class)
class CourierSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CourierExecutionService courierExecutionService;

    @MockBean
    private CourierProfileOperations courierProfileOperations;

    @MockBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    private static final UUID PARCEL_ID = UUID.fromString("7e9b3a24-c189-4b68-b7c1-fa9e6a0d2f11");
    private static final String COURIER_ID = "courier-42";

    // ==========================================
    // 1. Unauthenticated calls must return 401
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/courier/deliveries returns 401 Unauthorized when unauthenticated")
    void deliveries_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/courier/deliveries"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/courier/deliveries/{id} returns 401 Unauthorized when unauthenticated")
    void deliveryDetail_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/courier/deliveries/{id}", PARCEL_ID))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/v1/courier/deliveries/{id}/status returns 401 Unauthorized when unauthenticated")
    void updateStatus_unauthenticatedReturns401() throws Exception {
        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest("DELIVERED", null, "Sunil Silva", "idem-1");
        mockMvc.perform(patch("/api/v1/courier/deliveries/{id}/status", PARCEL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/courier/deliveries/{id}/proof returns 401 Unauthorized when unauthenticated")
    void uploadProof_unauthenticatedReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "proof.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        mockMvc.perform(multipart("/api/v1/courier/deliveries/{id}/proof", PARCEL_ID)
                .file(file))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/courier/proofs/{id} returns 401 Unauthorized when unauthenticated")
    void getProof_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/courier/proofs/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // 2. Unauthorized roles must return 403
    // ==========================================

    @Test
    @WithMockUser(username = "customer-01", roles = {"CUSTOMER"})
    @DisplayName("GET /api/v1/courier/deliveries returns 403 Forbidden for CUSTOMER role")
    void deliveries_forbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/courier/deliveries"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "merchant-01", roles = {"MERCHANT"})
    @DisplayName("GET /api/v1/courier/deliveries returns 403 Forbidden for MERCHANT role")
    void deliveries_forbiddenForMerchant() throws Exception {
        mockMvc.perform(get("/api/v1/courier/deliveries"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "dispatcher-01", roles = {"DISPATCHER"})
    @DisplayName("GET /api/v1/courier/deliveries returns 403 Forbidden for DISPATCHER role")
    void deliveries_forbiddenForDispatcher() throws Exception {
        mockMvc.perform(get("/api/v1/courier/deliveries"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin-01", roles = {"ADMIN"})
    @DisplayName("PATCH /api/v1/courier/deliveries/{id}/status returns 403 Forbidden for ADMIN role")
    void updateStatus_forbiddenForAdmin() throws Exception {
        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest("DELIVERED", null, "Sunil Silva", "idem-1");
        mockMvc.perform(patch("/api/v1/courier/deliveries/{id}/status", PARCEL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer-01", roles = {"CUSTOMER"})
    @DisplayName("GET /api/v1/courier/proofs/{id} returns 403 Forbidden for CUSTOMER role")
    void getProof_forbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/courier/proofs/{id}", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    // ==========================================
    // 3. Authorized roles are allowed
    // ==========================================

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("GET /api/v1/courier/deliveries allows COURIER role")
    void deliveries_allowsCourier() throws Exception {
        when(courierExecutionService.getTodaysDeliveries(eq(COURIER_ID), any(), any()))
            .thenReturn(new CourierDeliveriesResponse("2026-11-13", 0, 0, 0, List.of()));

        mockMvc.perform(get("/api/v1/courier/deliveries"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("PATCH /api/v1/courier/deliveries/{id}/status allows COURIER role")
    void updateStatus_allowsCourier() throws Exception {
        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest("DELIVERED", null, "Sunil Silva", "idem-1");
        when(courierExecutionService.updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any()))
            .thenReturn(new CourierStatusUpdateResponse(PARCEL_ID, "SD-1", "OUT_FOR_DELIVERY", "DELIVERED", Instant.now(), List.of()));

        mockMvc.perform(patch("/api/v1/courier/deliveries/{id}/status", PARCEL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("POST /api/v1/courier/deliveries/{id}/proof allows COURIER role")
    void uploadProof_allowsCourier() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "proof.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        when(courierExecutionService.uploadProof(eq(COURIER_ID), eq(PARCEL_ID), any(), any(), any()))
            .thenReturn(new ProofUploadResponse(UUID.randomUUID(), PARCEL_ID, "/proofs/1", "image/jpeg", 4L, Instant.now()));

        mockMvc.perform(multipart("/api/v1/courier/deliveries/{id}/proof", PARCEL_ID)
                .file(file))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = COURIER_ID, roles = {"COURIER"})
    @DisplayName("GET /api/v1/courier/proofs/{id} allows COURIER role")
    void getProof_allowsCourier() throws Exception {
        UUID proofId = UUID.randomUUID();
        when(courierProfileOperations.getProof(proofId.toString()))
            .thenReturn(ResponseEntity.ok(new ByteArrayResource(new byte[]{1, 2, 3})));

        mockMvc.perform(get("/api/v1/courier/proofs/{id}", proofId))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "dispatcher-01", roles = {"DISPATCHER"})
    @DisplayName("GET /api/v1/courier/proofs/{id} allows DISPATCHER role")
    void getProof_allowsDispatcher() throws Exception {
        UUID proofId = UUID.randomUUID();
        when(courierProfileOperations.getProof(proofId.toString()))
            .thenReturn(ResponseEntity.ok(new ByteArrayResource(new byte[]{1, 2, 3})));

        mockMvc.perform(get("/api/v1/courier/proofs/{id}", proofId))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin-01", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/courier/proofs/{id} allows ADMIN role")
    void getProof_allowsAdmin() throws Exception {
        UUID proofId = UUID.randomUUID();
        when(courierProfileOperations.getProof(proofId.toString()))
            .thenReturn(ResponseEntity.ok(new ByteArrayResource(new byte[]{1, 2, 3})));

        mockMvc.perform(get("/api/v1/courier/proofs/{id}", proofId))
            .andExpect(status().isOk());
    }
}
