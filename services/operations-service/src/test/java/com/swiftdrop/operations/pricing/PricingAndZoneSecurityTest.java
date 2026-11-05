package com.swiftdrop.operations.pricing;

import com.swiftdrop.operations.SecurityConfig;
import com.swiftdrop.operations.zones.AuditLogRepository;
import com.swiftdrop.operations.zones.ZoneAuditService;
import com.swiftdrop.operations.zones.ZoneController;
import com.swiftdrop.operations.zones.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ZoneController.class, PricingRuleController.class})
@Import({SecurityConfig.class, com.swiftdrop.operations.zones.OperationsMethodSecurity.class})
class PricingAndZoneSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ZoneRepository zoneRepository;

    @MockBean
    private ZoneAuditService zoneAuditService;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private PricingRuleRepository pricingRuleRepository;

    @MockBean
    private PricingAuditService pricingAuditService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListZonesAndPricingRules() throws Exception {
        mockMvc.perform(get("/api/v1/admin/zones")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/pricing-rules")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void dispatcherCanListZonesAndPricingRules() throws Exception {
        mockMvc.perform(get("/api/v1/admin/zones")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/pricing-rules")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "COURIER")
    void courierCannotManageZonesOrPricingRules() throws Exception {
        mockMvc.perform(get("/api/v1/admin/zones")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/pricing-rules")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotManageZonesOrPricingRules() throws Exception {
        mockMvc.perform(get("/api/v1/admin/zones")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/pricing-rules")).andExpect(status().isForbidden());
    }
}
