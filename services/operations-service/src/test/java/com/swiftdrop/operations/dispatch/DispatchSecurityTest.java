package com.swiftdrop.operations.dispatch;

import com.swiftdrop.operations.dispatch.dto.AssignmentResult;
import com.swiftdrop.operations.dispatch.dto.CourierWorkloadItem;
import com.swiftdrop.operations.dispatch.dto.FailedParcelsPage;
import com.swiftdrop.operations.dispatch.dto.UnassignedParcelsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DispatchController.class)
@Import(DispatchSecurityConfig.class)
class DispatchSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DispatchService dispatchService;

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("GET /api/v1/dispatch/parcels returns 200 for DISPATCHER role")
    void dispatchParcels_allowsDispatcher() throws Exception {
        when(dispatchService.listUnassignedParcels(any(), any(), anyInt(), anyInt()))
            .thenReturn(new UnassignedParcelsPage(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/api/v1/dispatch/parcels"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/dispatch/parcels returns 403 Forbidden for ADMIN role")
    void dispatchParcels_forbiddenForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "courier", roles = {"COURIER"})
    @DisplayName("GET /api/v1/dispatch/parcels returns 403 Forbidden for COURIER role")
    void dispatchParcels_forbiddenForCourier() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    @DisplayName("GET /api/v1/dispatch/parcels returns 403 Forbidden for CUSTOMER role")
    void dispatchParcels_forbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "merchant", roles = {"MERCHANT"})
    @DisplayName("GET /api/v1/dispatch/parcels returns 403 Forbidden for MERCHANT role")
    void dispatchParcels_forbiddenForMerchant() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/dispatch/parcels rejects unauthenticated request")
    void dispatchParcels_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("GET /api/v1/dispatch/parcels/failed returns 200 for DISPATCHER role")
    void failedParcels_allowsDispatcher() throws Exception {
        when(dispatchService.listFailedParcels(any(), any(), anyInt(), anyInt()))
            .thenReturn(new FailedParcelsPage(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/api/v1/dispatch/parcels/failed"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/dispatch/parcels/failed returns 403 Forbidden for ADMIN role")
    void failedParcels_forbiddenForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels/failed"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    @DisplayName("GET /api/v1/dispatch/parcels/failed returns 403 Forbidden for CUSTOMER role")
    void failedParcels_forbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels/failed"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "merchant", roles = {"MERCHANT"})
    @DisplayName("GET /api/v1/dispatch/parcels/failed returns 403 Forbidden for MERCHANT role")
    void failedParcels_forbiddenForMerchant() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels/failed"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "courier", roles = {"COURIER"})
    @DisplayName("GET /api/v1/dispatch/parcels/failed returns 403 Forbidden for COURIER role")
    void failedParcels_forbiddenForCourier() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels/failed"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/dispatch/parcels/failed rejects unauthenticated request")
    void failedParcels_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels/failed"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("POST /api/v1/dispatch/assignments returns 200 for DISPATCHER role")
    void assignParcel_allowsDispatcher() throws Exception {
        when(dispatchService.assignParcel(any(), anyString()))
            .thenReturn(new AssignmentResult("p-1", "c-1", Instant.now(), "ASSIGNED"));

        mockMvc.perform(post("/api/v1/dispatch/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parcelId\": \"a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d\", \"courierId\": \"f8e7d6c5-b4a3-2109-8765-43210fedcba9\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/v1/dispatch/assignments returns 403 Forbidden for ADMIN role")
    void assignParcel_forbiddenForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parcelId\": \"a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d\", \"courierId\": \"f8e7d6c5-b4a3-2109-8765-43210fedcba9\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "courier", roles = {"COURIER"})
    @DisplayName("POST /api/v1/dispatch/assignments returns 403 Forbidden for COURIER role")
    void assignParcel_forbiddenForCourier() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parcelId\": \"a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d\", \"courierId\": \"f8e7d6c5-b4a3-2109-8765-43210fedcba9\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    @DisplayName("POST /api/v1/dispatch/assignments returns 403 Forbidden for CUSTOMER role")
    void assignParcel_forbiddenForCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parcelId\": \"a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d\", \"courierId\": \"f8e7d6c5-b4a3-2109-8765-43210fedcba9\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "merchant", roles = {"MERCHANT"})
    @DisplayName("POST /api/v1/dispatch/assignments returns 403 Forbidden for MERCHANT role")
    void assignParcel_forbiddenForMerchant() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parcelId\": \"a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d\", \"courierId\": \"f8e7d6c5-b4a3-2109-8765-43210fedcba9\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/assignments rejects unauthenticated request")
    void assignParcel_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parcelId\": \"a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d\", \"courierId\": \"f8e7d6c5-b4a3-2109-8765-43210fedcba9\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("PATCH /api/v1/dispatch/couriers/{id}/availability returns 200 for DISPATCHER role")
    void updateAvailability_allowsDispatcher() throws Exception {
        when(dispatchService.updateCourierAvailability(anyString(), anyBoolean()))
            .thenReturn(new CourierWorkloadItem("c-1", "Kamal", "+94771234567", "COLOMBO", 0, true, "VAN"));

        mockMvc.perform(patch("/api/v1/dispatch/couriers/f8e7d6c5-b4a3-2109-8765-43210fedcba9/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\": true}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("PATCH /api/v1/dispatch/couriers/{id}/availability returns 403 Forbidden for ADMIN role")
    void updateAvailability_forbiddenForAdmin() throws Exception {
        mockMvc.perform(patch("/api/v1/dispatch/couriers/f8e7d6c5-b4a3-2109-8765-43210fedcba9/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\": true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "courier", roles = {"COURIER"})
    @DisplayName("PATCH /api/v1/dispatch/couriers/{id}/availability returns 403 Forbidden for COURIER role")
    void updateAvailability_forbiddenForCourier() throws Exception {
        mockMvc.perform(patch("/api/v1/dispatch/couriers/f8e7d6c5-b4a3-2109-8765-43210fedcba9/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\": true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    @DisplayName("PATCH /api/v1/dispatch/couriers/{id}/availability returns 403 Forbidden for CUSTOMER role")
    void updateAvailability_forbiddenForCustomer() throws Exception {
        mockMvc.perform(patch("/api/v1/dispatch/couriers/f8e7d6c5-b4a3-2109-8765-43210fedcba9/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\": true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "merchant", roles = {"MERCHANT"})
    @DisplayName("PATCH /api/v1/dispatch/couriers/{id}/availability returns 403 Forbidden for MERCHANT role")
    void updateAvailability_forbiddenForMerchant() throws Exception {
        mockMvc.perform(patch("/api/v1/dispatch/couriers/f8e7d6c5-b4a3-2109-8765-43210fedcba9/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\": true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/dispatch/couriers/{id}/availability rejects unauthenticated request")
    void updateAvailability_unauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/dispatch/couriers/f8e7d6c5-b4a3-2109-8765-43210fedcba9/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\": true}"))
            .andExpect(status().isForbidden());
    }
}
