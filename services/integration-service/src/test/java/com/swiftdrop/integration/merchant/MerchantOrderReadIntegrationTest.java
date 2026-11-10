package com.swiftdrop.integration.merchant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class MerchantOrderReadIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantOrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private JwtDecoder jwtDecoder;

    private UUID merchantA;
    private UUID merchantB;
    private Instant now;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("delete from idempotency_keys");
        orderRepository.deleteAll();
        merchantRepository.deleteAll();

        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        merchantA = createMerchant("merchant-a@example.test");
        merchantB = createMerchant("merchant-b@example.test");
    }

    private UUID createMerchant(String email) {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Test Store", "Contact", email,
                "+94771234567", null, Instant.now());
        merchant.approve("client-" + UUID.randomUUID());
        return merchantRepository.saveAndFlush(merchant).getId();
    }

    private MerchantOrderEntity createOrder(UUID merchantId, String trackingNumber,
                                            MerchantOrderStatus status, BigDecimal fee, Instant createdAt) {
        return orderRepository.saveAndFlush(new MerchantOrderEntity(
                UUID.randomUUID(),
                merchantId,
                UUID.randomUUID(),
                trackingNumber,
                "ref-" + UUID.randomUUID(),
                status,
                fee,
                createdAt
        ));
    }

    private MockHttpServletRequestBuilder merchantGet(String url, UUID merchantId) {
        return get(url).with(jwt().jwt(token -> token
                .subject("client-" + merchantId)
                .claim("role", "MERCHANT")
                .claim("roles", List.of("ROLE_MERCHANT"))
                .claim("merchantId", merchantId.toString())));
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders returns 200 OK with paginated orders for authenticated merchant")
    void listOrders_success() throws Exception {
        createOrder(merchantA, "SD-20261028-0001", MerchantOrderStatus.CREATED, new BigDecimal("450.00"), now.minus(2, ChronoUnit.HOURS));
        createOrder(merchantA, "SD-20261028-0002", MerchantOrderStatus.IN_TRANSIT, new BigDecimal("650.00"), now.minus(1, ChronoUnit.HOURS));

        mockMvc.perform(merchantGet("/api/v1/merchant/orders", merchantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-20261028-0002"))
                .andExpect(jsonPath("$.content[0].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.content[0].fee").value(650.00))
                .andExpect(jsonPath("$.content[1].trackingNumber").value("SD-20261028-0001"))
                .andExpect(jsonPath("$.content[1].status").value("CREATED"))
                .andExpect(jsonPath("$.content[1].fee").value(450.00));
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders filters by status")
    void listOrders_filterByStatus() throws Exception {
        createOrder(merchantA, "SD-20261028-0001", MerchantOrderStatus.CREATED, new BigDecimal("450.00"), now);
        createOrder(merchantA, "SD-20261028-0002", MerchantOrderStatus.DELIVERED, new BigDecimal("550.00"), now);
        createOrder(merchantA, "SD-20261028-0003", MerchantOrderStatus.DELIVERED, new BigDecimal("650.00"), now);

        mockMvc.perform(merchantGet("/api/v1/merchant/orders?status=DELIVERED", merchantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.content[1].status").value("DELIVERED"));
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders searches by tracking number (case-insensitive substring)")
    void listOrders_searchByTrackingNumber() throws Exception {
        createOrder(merchantA, "SD-20261028-0001", MerchantOrderStatus.CREATED, new BigDecimal("450.00"), now);
        createOrder(merchantA, "SD-20261028-9999", MerchantOrderStatus.IN_TRANSIT, new BigDecimal("550.00"), now);

        mockMvc.perform(merchantGet("/api/v1/merchant/orders?trackingNumber=9999", merchantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-20261028-9999"));
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders filters by date range (from and to)")
    void listOrders_filterByDateRange() throws Exception {
        createOrder(merchantA, "SD-20261028-0001", MerchantOrderStatus.CREATED, new BigDecimal("450.00"), now.minus(5, ChronoUnit.DAYS));
        createOrder(merchantA, "SD-20261028-0002", MerchantOrderStatus.IN_TRANSIT, new BigDecimal("550.00"), now.minus(2, ChronoUnit.DAYS));
        createOrder(merchantA, "SD-20261028-0003", MerchantOrderStatus.DELIVERED, new BigDecimal("650.00"), now);

        String from = now.minus(3, ChronoUnit.DAYS).toString();
        String to = now.minus(1, ChronoUnit.DAYS).toString();

        mockMvc.perform(merchantGet("/api/v1/merchant/orders?from=" + from + "&to=" + to, merchantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-20261028-0002"));
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders paginates results correctly")
    void listOrders_pagination() throws Exception {
        createOrder(merchantA, "SD-20261028-0001", MerchantOrderStatus.CREATED, new BigDecimal("100.00"), now.minus(3, ChronoUnit.HOURS));
        createOrder(merchantA, "SD-20261028-0002", MerchantOrderStatus.CREATED, new BigDecimal("200.00"), now.minus(2, ChronoUnit.HOURS));
        createOrder(merchantA, "SD-20261028-0003", MerchantOrderStatus.CREATED, new BigDecimal("300.00"), now.minus(1, ChronoUnit.HOURS));

        // Page 0, size 2
        mockMvc.perform(merchantGet("/api/v1/merchant/orders?page=0&size=2", merchantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-20261028-0003"))
                .andExpect(jsonPath("$.content[1].trackingNumber").value("SD-20261028-0002"));

        // Page 1, size 2
        mockMvc.perform(merchantGet("/api/v1/merchant/orders?page=1&size=2", merchantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-20261028-0001"));
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders returns empty content when no orders match")
    void listOrders_emptyResults() throws Exception {
        mockMvc.perform(merchantGet("/api/v1/merchant/orders", merchantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders/counts returns aggregate status counts for authenticated merchant")
    void getOrderCounts_success() throws Exception {
        createOrder(merchantA, "SD-01", MerchantOrderStatus.CREATED, new BigDecimal("100.00"), now);
        createOrder(merchantA, "SD-02", MerchantOrderStatus.PICKED_UP, new BigDecimal("100.00"), now);
        createOrder(merchantA, "SD-03", MerchantOrderStatus.IN_TRANSIT, new BigDecimal("100.00"), now);
        createOrder(merchantA, "SD-04", MerchantOrderStatus.OUT_FOR_DELIVERY, new BigDecimal("100.00"), now);
        createOrder(merchantA, "SD-05", MerchantOrderStatus.DELIVERED, new BigDecimal("100.00"), now);
        createOrder(merchantA, "SD-06", MerchantOrderStatus.DELIVERED, new BigDecimal("100.00"), now);
        createOrder(merchantA, "SD-07", MerchantOrderStatus.FAILED, new BigDecimal("100.00"), now);
        createOrder(merchantA, "SD-08", MerchantOrderStatus.CANCELLED, new BigDecimal("100.00"), now);

        mockMvc.perform(merchantGet("/api/v1/merchant/orders/counts", merchantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.pickedUp").value(1))
                .andExpect(jsonPath("$.inTransit").value(1))
                .andExpect(jsonPath("$.outForDelivery").value(1))
                .andExpect(jsonPath("$.delivered").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.cancelled").value(1));
    }

    @Test
    @DisplayName("Strict Cross-Merchant Isolation: Merchant A cannot view or count Merchant B's orders")
    void crossMerchantIsolation() throws Exception {
        createOrder(merchantA, "SD-A-001", MerchantOrderStatus.CREATED, new BigDecimal("100.00"), now);
        createOrder(merchantB, "SD-B-001", MerchantOrderStatus.DELIVERED, new BigDecimal("200.00"), now.minus(1, ChronoUnit.HOURS));
        createOrder(merchantB, "SD-B-002", MerchantOrderStatus.DELIVERED, new BigDecimal("300.00"), now);

        // Merchant A listing
        mockMvc.perform(merchantGet("/api/v1/merchant/orders", merchantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-A-001"));

        // Merchant B listing
        mockMvc.perform(merchantGet("/api/v1/merchant/orders", merchantB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-B-002"))
                .andExpect(jsonPath("$.content[1].trackingNumber").value("SD-B-001"));

        // Merchant A counts
        mockMvc.perform(merchantGet("/api/v1/merchant/orders/counts", merchantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.delivered").value(0));

        // Merchant B counts
        mockMvc.perform(merchantGet("/api/v1/merchant/orders/counts", merchantB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.delivered").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders returns 401 Unauthorized when unauthenticated")
    void listOrders_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders returns 403 Forbidden when caller role is CUSTOMER")
    void listOrders_forbidden_wrongRole() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/orders")
                .with(jwt().jwt(token -> token
                        .subject("customer-123")
                        .claim("role", "CUSTOMER")
                        .claim("roles", List.of("ROLE_CUSTOMER"))
                        .claim("merchantId", merchantA.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders returns 403 Forbidden when merchantId claim is missing")
    void listOrders_forbidden_missingMerchantIdClaim() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/orders")
                .with(jwt().jwt(token -> token
                        .subject("client-123")
                        .claim("role", "MERCHANT")
                        .claim("roles", List.of("ROLE_MERCHANT")))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/merchant/orders returns 400 Bad Request on invalid pagination params")
    void listOrders_invalidPagination() throws Exception {
        mockMvc.perform(merchantGet("/api/v1/merchant/orders?page=-1", merchantA))
                .andExpect(status().isBadRequest());

        mockMvc.perform(merchantGet("/api/v1/merchant/orders?size=0", merchantA))
                .andExpect(status().isBadRequest());

        mockMvc.perform(merchantGet("/api/v1/merchant/orders?size=101", merchantA))
                .andExpect(status().isBadRequest());
    }
}
