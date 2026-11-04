package com.swiftdrop.operations.dispatch;

import com.swiftdrop.operations.dispatch.dto.UnassignedParcelsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @DisplayName("GET /api/v1/dispatch/parcels rejects unauthenticated request")
    void dispatchParcels_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels"))
            .andExpect(status().isForbidden());
    }
}
