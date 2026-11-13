package com.swiftdrop.operations.zones;

import com.swiftdrop.common.GlobalExceptionHandler;
import com.swiftdrop.operations.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ZoneController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, OperationsMethodSecurity.class})
class ZoneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ZoneRepository repository;

    @MockBean
    private ZoneAuditService auditService;

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void rejectsZoneWithoutBaseFeeUsingValidationErrorResponse() throws Exception {
        mockMvc.perform(post("/api/v1/admin/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "COLOMBO",
                                  "districts": ["Colombo"],
                                  "served": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("baseFeeLkr")))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(repository, never()).save(any(Zone.class));
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void preservesNonNegativeBaseFeeValidation() throws Exception {
        mockMvc.perform(post("/api/v1/admin/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "COLOMBO",
                                  "districts": ["Colombo"],
                                  "served": true,
                                  "baseFeeLkr": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("baseFeeLkr")))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(repository, never()).save(any(Zone.class));
    }
}
