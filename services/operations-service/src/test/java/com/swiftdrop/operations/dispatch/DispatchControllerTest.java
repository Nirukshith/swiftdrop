package com.swiftdrop.operations.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.operations.dispatch.client.CourierUnavailableException;
import com.swiftdrop.operations.dispatch.client.ParcelAlreadyAssignedException;
import com.swiftdrop.operations.dispatch.client.ParcelNotFoundException;
import com.swiftdrop.operations.dispatch.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DispatchController.class)
@AutoConfigureMockMvc(addFilters = false)
class DispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DispatchService dispatchService;

    private static final String PARCEL_ID = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d";
    private static final String COURIER_ID = "f8e7d6c5-b4a3-2109-8765-43210fedcba9";

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("GET /api/v1/dispatch/parcels returns 200 with unassigned page")
    void listUnassignedParcels_returnsOk() throws Exception {
        UnassignedParcelsPage page = new UnassignedParcelsPage(
            List.of(new DispatchParcelSummary(
                PARCEL_ID,
                "SD-20261028-001",
                "CREATED",
                "Colombo",
                "Kandy",
                "10 Main St",
                "20 Hill St",
                2.5,
                0,
                null,
                Instant.now()
            )),
            1,
            1,
            0,
            20
        );

        when(dispatchService.listUnassignedParcels(any(), any(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/dispatch/parcels")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].parcelId").value(PARCEL_ID))
            .andExpect(jsonPath("$.content[0].status").value("CREATED"));
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("GET /api/v1/dispatch/couriers returns 200 with workload board")
    void getCourierWorkloadBoard_returnsOk() throws Exception {
        List<CourierWorkloadItem> board = List.of(new CourierWorkloadItem(
            COURIER_ID,
            "Saman Perera",
            "+94771234567",
            "ZONE-COLOMBO-NORTH",
            2,
            true,
            "MOTORCYCLE"
        ));

        when(dispatchService.getCourierWorkloadBoard(any(), anyBoolean())).thenReturn(board);

        mockMvc.perform(get("/api/v1/dispatch/couriers")
                .param("zone", "ZONE-COLOMBO-NORTH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].courierId").value(COURIER_ID))
            .andExpect(jsonPath("$[0].activeCount").value(2))
            .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("POST /api/v1/dispatch/assignments returns 200 on success")
    void assignParcel_returnsOk() throws Exception {
        AssignParcelRequest request = new AssignParcelRequest(PARCEL_ID, COURIER_ID);
        AssignmentResult result = new AssignmentResult(PARCEL_ID, COURIER_ID, Instant.now(), "ASSIGNED");

        when(dispatchService.assignParcel(eq(request), anyString())).thenReturn(result);

        mockMvc.perform(post("/api/v1/dispatch/assignments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parcelId").value(PARCEL_ID))
            .andExpect(jsonPath("$.courierId").value(COURIER_ID))
            .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("POST /api/v1/dispatch/assignments returns 409 when parcel is already assigned")
    void assignParcel_returns409_whenAlreadyAssigned() throws Exception {
        AssignParcelRequest request = new AssignParcelRequest(PARCEL_ID, COURIER_ID);

        when(dispatchService.assignParcel(eq(request), anyString()))
            .thenThrow(new ParcelAlreadyAssignedException("Parcel has already been assigned to another courier."));

        mockMvc.perform(post("/api/v1/dispatch/assignments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PARCEL_ALREADY_ASSIGNED"))
            .andExpect(jsonPath("$.message").value("Parcel has already been assigned to another courier."));
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("POST /api/v1/dispatch/assignments returns 400 when courier is unavailable")
    void assignParcel_returns400_whenCourierUnavailable() throws Exception {
        AssignParcelRequest request = new AssignParcelRequest(PARCEL_ID, COURIER_ID);

        when(dispatchService.assignParcel(eq(request), anyString()))
            .thenThrow(new CourierUnavailableException("Courier is currently unavailable for assignments."));

        mockMvc.perform(post("/api/v1/dispatch/assignments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COURIER_UNAVAILABLE"));
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("PATCH /api/v1/dispatch/couriers/{id}/availability returns 200 with updated item")
    void updateCourierAvailability_returnsOk() throws Exception {
        CourierAvailabilityUpdateRequest request = new CourierAvailabilityUpdateRequest(false);
        CourierWorkloadItem item = new CourierWorkloadItem(
            COURIER_ID,
            "Saman Perera",
            "+94771234567",
            "ZONE-COLOMBO-NORTH",
            1,
            false,
            "MOTORCYCLE"
        );

        when(dispatchService.updateCourierAvailability(eq(COURIER_ID), eq(false))).thenReturn(item);

        mockMvc.perform(patch("/api/v1/dispatch/couriers/{id}/availability", COURIER_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courierId").value(COURIER_ID))
            .andExpect(jsonPath("$.available").value(false));
    }
}
