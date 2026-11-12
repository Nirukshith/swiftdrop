package com.swiftdrop.operations.pricing;

import com.swiftdrop.operations.SecurityConfig;
import com.swiftdrop.operations.zones.AuditLogRepository;
import com.swiftdrop.operations.zones.ZoneAuditService;
import com.swiftdrop.operations.zones.ZoneController;
import com.swiftdrop.operations.zones.ZoneRepository;
import com.swiftdrop.operations.zones.Zone;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = {ZoneController.class, PricingRuleController.class, QuoteController.class})
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

    @Test
    void unauthenticatedCannotManageZonesOrPricingRules() throws Exception {
        mockMvc.perform(get("/api/v1/admin/zones")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/pricing-rules")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void dispatcherCanAccessInternalPricingQuote() throws Exception {
        Zone pickup = new Zone("COLOMBO", Set.of("Colombo"), true, new BigDecimal("500.00"));
        Zone dropoff = new Zone("GAMPAHA", Set.of("Gampaha"), true, new BigDecimal("500.00"));
        when(zoneRepository.findByDistrictsContaining("Colombo")).thenReturn(Optional.of(pickup));
        when(zoneRepository.findByDistrictsContaining("Gampaha")).thenReturn(Optional.of(dropoff));
        when(pricingRuleRepository.findByActiveTrue()).thenReturn(List.of(
                new PricingRule(PricingRule.Kind.WEIGHT_BAND, "UP_TO_5KG",
                        BigDecimal.ZERO, new BigDecimal("5.00"), new BigDecimal("200.00"), true),
                new PricingRule(PricingRule.Kind.SIZE, "MEDIUM", null, null,
                        new BigDecimal("150.00"), true)));

        mockMvc.perform(get("/internal/v1/pricing/quote")
                        .param("pickupDistrict", "Colombo")
                        .param("dropoffDistrict", "Gampaha")
                        .param("weightKg", "2.5")
                        .param("size", "MEDIUM"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_pricing:read")
    void pricingScopeCanAccessInternalPricingQuote() throws Exception {
        Zone pickup = new Zone("COLOMBO", Set.of("Colombo"), true, new BigDecimal("500.00"));
        Zone dropoff = new Zone("GAMPAHA", Set.of("Gampaha"), true, new BigDecimal("500.00"));
        when(zoneRepository.findByDistrictsContaining("Colombo")).thenReturn(Optional.of(pickup));
        when(zoneRepository.findByDistrictsContaining("Gampaha")).thenReturn(Optional.of(dropoff));
        when(pricingRuleRepository.findByActiveTrue()).thenReturn(List.of(
                new PricingRule(PricingRule.Kind.WEIGHT_BAND, "UP_TO_5KG",
                        BigDecimal.ZERO, new BigDecimal("5.00"), new BigDecimal("200.00"), true),
                new PricingRule(PricingRule.Kind.SIZE, "MEDIUM", null, null,
                        new BigDecimal("150.00"), true)));

        mockMvc.perform(get("/internal/v1/pricing/quote")
                        .param("pickupDistrict", "Colombo")
                        .param("dropoffDistrict", "Gampaha")
                        .param("weightKg", "2.5")
                        .param("size", "MEDIUM"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_other")
    void authenticatedTokenWithoutPricingScopeOrDispatcherRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/internal/v1/pricing/quote")
                        .param("pickupDistrict", "Colombo")
                        .param("dropoffDistrict", "Gampaha")
                        .param("weightKg", "2.5")
                        .param("size", "MEDIUM"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotAccessDispatcherOnlyInternalPricingQuote() throws Exception {
        mockMvc.perform(get("/internal/v1/pricing/quote")
                        .param("pickupDistrict", "Colombo")
                        .param("dropoffDistrict", "Gampaha")
                        .param("weightKg", "2.5")
                        .param("size", "MEDIUM"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAccessInternalPricingQuote() throws Exception {
        mockMvc.perform(get("/internal/v1/pricing/quote")
                        .param("pickupDistrict", "Colombo")
                        .param("dropoffDistrict", "Gampaha")
                        .param("weightKg", "2.5")
                        .param("size", "MEDIUM"))
                .andExpect(status().isUnauthorized());
    }
}
