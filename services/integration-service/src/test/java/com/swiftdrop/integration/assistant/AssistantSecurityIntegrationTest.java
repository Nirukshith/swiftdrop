package com.swiftdrop.integration.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.integration.SecurityConfig;
import com.swiftdrop.integration.assistant.client.FakeLlmClient;
import com.swiftdrop.integration.assistant.controller.AssistantController;
import com.swiftdrop.integration.assistant.dto.ChatMessageRequest;
import com.swiftdrop.integration.assistant.mcp.McpServerController;
import com.swiftdrop.integration.assistant.service.AssistantService;
import com.swiftdrop.integration.assistant.tools.AssistantTools;
import com.swiftdrop.integration.assistant.tools.AssistantToolsImpl;
import com.swiftdrop.integration.assistant.tools.ParcelServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AssistantController.class, McpServerController.class})
@Import(SecurityConfig.class)
class AssistantSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ParcelServiceClient parcelClient;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static final String CUSTOMER_A_ID = "cust-tenant-aaa";
    private static final String CUSTOMER_A_TOKEN = "jwt-token-cust-a";
    private static final String PARCEL_A_TRACKING = "SD-20261113-AAA111";

    private static final String CUSTOMER_B_ID = "cust-tenant-bbb";
    private static final String CUSTOMER_B_TOKEN = "jwt-token-cust-b";
    private static final String PARCEL_B_TRACKING = "SD-20261113-BBB222";

    @BeforeEach
    void setUp() {
        // Customer A owns PARCEL_A
        var itemA = new ParcelServiceClient.ParcelListItem(
                "pcl-aaa", PARCEL_A_TRACKING, "OUT_FOR_DELIVERY", "courier-1",
                Instant.now(), Instant.now(), BigDecimal.valueOf(500)
        );
        var detailA = new ParcelServiceClient.ParcelDetail(
                "pcl-aaa", PARCEL_A_TRACKING, "OUT_FOR_DELIVERY", "courier-1",
                new ParcelServiceClient.Address("1 Main St", null, "Colombo", "Colombo", "00100"),
                Instant.now(), Instant.now(), BigDecimal.valueOf(500)
        );
        var historyA = new ParcelServiceClient.ParcelHistory(
                PARCEL_A_TRACKING, "OUT_FOR_DELIVERY",
                List.of(new ParcelServiceClient.HistoryItem("CREATED", Instant.now().minusSeconds(3600)),
                        new ParcelServiceClient.HistoryItem("OUT_FOR_DELIVERY", Instant.now()))
        );

        // Customer B owns PARCEL_B
        var itemB = new ParcelServiceClient.ParcelListItem(
                "pcl-bbb", PARCEL_B_TRACKING, "DELIVERED", "courier-2",
                Instant.now(), Instant.now(), BigDecimal.valueOf(750)
        );
        var detailB = new ParcelServiceClient.ParcelDetail(
                "pcl-bbb", PARCEL_B_TRACKING, "DELIVERED", "courier-2",
                new ParcelServiceClient.Address("5 Galle Rd", null, "Kandy", "Kandy", "20000"),
                Instant.now(), Instant.now(), BigDecimal.valueOf(750)
        );

        when(parcelClient.list(isNull(), anyInt(), anyInt(), eq(CUSTOMER_A_TOKEN)))
                .thenReturn(new ParcelServiceClient.ParcelPage(List.of(itemA), 1, 1, 0, 50));
        when(parcelClient.detail(eq("pcl-aaa"), eq(CUSTOMER_A_TOKEN)))
                .thenReturn(detailA);
        when(parcelClient.history(eq("pcl-aaa"), eq(CUSTOMER_A_TOKEN)))
                .thenReturn(historyA);

        when(parcelClient.list(isNull(), anyInt(), anyInt(), eq(CUSTOMER_B_TOKEN)))
                .thenReturn(new ParcelServiceClient.ParcelPage(List.of(itemB), 1, 1, 0, 50));
        when(parcelClient.detail(eq("pcl-bbb"), eq(CUSTOMER_B_TOKEN)))
                .thenReturn(detailB);

        // Quote mock
        when(parcelClient.quote(anyString(), anyString(), anyDouble(), anyString(), anyString()))
                .thenReturn(new ParcelServiceClient.Quote(BigDecimal.valueOf(650), "LKR"));
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestBeans {
        @org.springframework.context.annotation.Bean
        public AssistantTools assistantTools(ParcelServiceClient parcelClient) {
            return new AssistantToolsImpl(parcelClient);
        }

        @org.springframework.context.annotation.Bean
        public AssistantService assistantService(AssistantTools tools) {
            return new AssistantService(new FakeLlmClient(), tools);
        }
    }

    // =========================================================================
    // 1. Multi-Tenant Cross-Customer Isolation Tests (Section 7 & 8)
    // =========================================================================

    @Test
    @DisplayName("Assistant Chat: Customer A successfully checks status of their own parcel")
    void assistantChat_customerCanQueryOwnParcel() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("Where is parcel " + PARCEL_A_TRACKING + "?", "conv-a");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_A_TOKEN)
                                .subject(CUSTOMER_A_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("OUT_FOR_DELIVERY")));
    }

    @Test
    @DisplayName("Assistant Chat: Customer B is rejected when querying Customer A's parcel")
    void assistantChat_customerCannotQueryAnotherCustomerParcel() throws Exception {
        // Customer B asks for Customer A's parcel tracking number
        ChatMessageRequest request = new ChatMessageRequest("Where is parcel " + PARCEL_A_TRACKING + "?", "conv-b");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_B_TOKEN)
                                .subject(CUSTOMER_B_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("could not find that parcel among your SwiftDrop parcels")));
    }

    @Test
    @DisplayName("MCP: Customer B receives -32004 when querying Customer A's parcel by tracking number")
    void mcp_customerCannotAccessAnotherCustomerParcel() throws Exception {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0",
                "id", "req-1",
                "method", "tools/call",
                "params", Map.of(
                        "name", "get_parcel_by_tracking_number",
                        "arguments", Map.of("trackingNumber", PARCEL_A_TRACKING)
                )
        );

        mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_B_TOKEN)
                                .subject(CUSTOMER_B_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rpcRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code", equalTo(-32004)))
                .andExpect(jsonPath("$.error.message", containsString("Parcel was not found for the authenticated customer")));
    }

    @Test
    @DisplayName("MCP: Customer B receives -32004 when requesting tracking history for Customer A's parcel")
    void mcp_customerCannotAccessAnotherCustomerTrackingHistory() throws Exception {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0",
                "id", "req-2",
                "method", "tools/call",
                "params", Map.of(
                        "name", "get_tracking_history",
                        "arguments", Map.of("trackingNumber", PARCEL_A_TRACKING)
                )
        );

        mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_B_TOKEN)
                                .subject(CUSTOMER_B_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rpcRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code", equalTo(-32004)));
    }

    @Test
    @DisplayName("MCP: Customer B receives -32004 when requesting delivery info for Customer A's parcel")
    void mcp_customerCannotAccessAnotherCustomerDeliveryInfo() throws Exception {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0",
                "id", "req-3",
                "method", "tools/call",
                "params", Map.of(
                        "name", "get_delivery_information",
                        "arguments", Map.of("trackingNumber", PARCEL_A_TRACKING)
                )
        );

        mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_B_TOKEN)
                                .subject(CUSTOMER_B_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rpcRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code", equalTo(-32004)));
    }

    // =========================================================================
    // 2. Role-Based Access Control on Assistant & MCP (Section 6, 7 & 9)
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/assistant/chat returns 401 when unauthenticated")
    void assistantChat_unauthenticated_returns401() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("Hello", null);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/assistant/chat returns 403 for non-customer roles")
    void assistantChat_nonCustomerRole_returns403() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("Where is my parcel?", null);

        for (String role : List.of("ROLE_DISPATCHER", "ROLE_COURIER", "ROLE_MERCHANT", "ROLE_ADMIN")) {
            mockMvc.perform(post("/api/v1/assistant/chat")
                            .with(jwt().jwt(builder -> builder
                                    .tokenValue("staff-token")
                                    .subject("staff-1")
                                    .claim("roles", List.of(role)))
                                    .authorities(new SimpleGrantedAuthority(role)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("POST /mcp returns 401 when unauthenticated")
    void mcp_unauthenticated_returns401() throws Exception {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "tools/list"
        );

        mockMvc.perform(post("/mcp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rpcRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /mcp returns 403 for non-customer roles")
    void mcp_nonCustomerRole_returns403() throws Exception {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "tools/list"
        );

        for (String role : List.of("ROLE_DISPATCHER", "ROLE_COURIER", "ROLE_MERCHANT", "ROLE_ADMIN")) {
            mockMvc.perform(post("/mcp")
                            .with(jwt().jwt(builder -> builder
                                    .tokenValue("token")
                                    .subject("user-1")
                                    .claim("roles", List.of(role)))
                                    .authorities(new SimpleGrantedAuthority(role)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(rpcRequest)))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 3. Prompt & Input Validation Hardening (Section 7)
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/assistant/chat rejects blank messages with 400 Bad Request")
    void assistantChat_blankMessage_returns400() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("   ", "conv-1");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_A_TOKEN)
                                .subject(CUSTOMER_A_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/assistant/chat rejects messages exceeding 500 characters with 400 Bad Request")
    void assistantChat_excessiveLength_returns400() throws Exception {
        String longMessage = "a".repeat(501);
        ChatMessageRequest request = new ChatMessageRequest(longMessage, "conv-1");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_A_TOKEN)
                                .subject(CUSTOMER_A_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 4. MCP Schema, Parameter & Error Code Hardening (Section 9)
    // =========================================================================

    @Test
    @DisplayName("MCP: Malformed tracking number returns -32602 Invalid tracking number")
    void mcp_malformedTrackingNumber_returnsInvalidParams() throws Exception {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0",
                "id", 10,
                "method", "tools/call",
                "params", Map.of(
                        "name", "get_parcel_by_tracking_number",
                        "arguments", Map.of("trackingNumber", "INVALID-TRACKING")
                )
        );

        mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_A_TOKEN)
                                .subject(CUSTOMER_A_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rpcRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code", equalTo(-32602)))
                .andExpect(jsonPath("$.error.message", containsString("Invalid tracking number")));
    }

    @Test
    @DisplayName("MCP: Negative page or excessive size (>50) returns -32602")
    void mcp_invalidPagination_returnsInvalidParams() throws Exception {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0",
                "id", 11,
                "method", "tools/call",
                "params", Map.of(
                        "name", "get_customer_parcels",
                        "arguments", Map.of("page", -1, "size", 100)
                )
        );

        mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_A_TOKEN)
                                .subject(CUSTOMER_A_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rpcRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code", equalTo(-32602)))
                .andExpect(jsonPath("$.error.message", containsString("page must be non-negative and size must be between 1 and 50")));
    }

    @Test
    @DisplayName("MCP: Missing required arguments returns -32602")
    void mcp_missingArguments_returnsInvalidParams() throws Exception {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0",
                "id", 12,
                "method", "tools/call",
                "params", Map.of(
                        "name", "estimate_delivery_fee",
                        "arguments", Map.of("pickupDistrict", "Colombo")
                )
        );

        mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_A_TOKEN)
                                .subject(CUSTOMER_A_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rpcRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code", equalTo(-32602)))
                .andExpect(jsonPath("$.error.message", containsString("destinationDistrict is required")));
    }

    @Test
    @DisplayName("MCP: Unknown method returns -32601 Method not supported")
    void mcp_unknownMethod_returnsMethodNotFound() throws Exception {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0",
                "id", 13,
                "method", "unknown/action"
        );

        mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_A_TOKEN)
                                .subject(CUSTOMER_A_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rpcRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code", equalTo(-32601)))
                .andExpect(jsonPath("$.error.message", containsString("Method is not supported")));
    }

    @Test
    @DisplayName("MCP: Invalid JSON-RPC protocol returns -32600")
    void mcp_invalidJsonRpc_returnsInvalidRequest() throws Exception {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "1.0",
                "id", 14,
                "method", "tools/list"
        );

        mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(builder -> builder
                                .tokenValue(CUSTOMER_A_TOKEN)
                                .subject(CUSTOMER_A_ID)
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rpcRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code", equalTo(-32600)))
                .andExpect(jsonPath("$.error.message", containsString("Invalid JSON-RPC request")));
    }
}
