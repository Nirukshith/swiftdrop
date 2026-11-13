package com.swiftdrop.parcel.tracking;

import com.swiftdrop.common.security.dev.DevJwtTokenMinter;
import com.swiftdrop.parcel.parcel.AddressSnapshot;
import com.swiftdrop.parcel.parcel.Parcel;
import com.swiftdrop.parcel.parcel.ParcelRepository;
import com.swiftdrop.parcel.parcel.ParcelSize;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import com.swiftdrop.parcel.parcel.PricingQuoteClient;
import com.swiftdrop.parcel.parcel.StatusHistory;
import com.swiftdrop.parcel.parcel.StatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Who may call each M2 endpoint, with real signed bearer tokens through parcel-service's
 * security chains (dev profile key). Rules from parcel-read.yaml:
 * <ul>
 *   <li>history: owner customer, assigned courier, DISPATCHER, ADMIN</li>
 *   <li>receipt and reading a rating: owner customer and ADMIN</li>
 *   <li>submitting a rating: owner customer only</li>
 *   <li>public lookup and stream: anyone; tokens are ignored there</li>
 * </ul>
 * Missing, expired or wrongly signed tokens get 401 on the authenticated endpoints.
 */
// The public endpoints are called once per caller from one address; the rate limit has its own test.
@SpringBootTest(properties = "swiftdrop.tracking.rate-limit.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class TrackingAccessMatrixIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String TRACKING_NO = "SD-20261113-ACC001";
    private static final String OWNER = "c1a2b3c4-0000-4000-8000-0000000000f1";
    private static final String OTHER_CUSTOMER = "c1a2b3c4-0000-4000-8000-0000000000f2";
    private static final String COURIER = "c1a2b3c4-0000-4000-8000-0000000000f3";
    private static final String OTHER_COURIER = "c1a2b3c4-0000-4000-8000-0000000000f4";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private StatusHistoryRepository statusHistoryRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DevJwtTokenMinter tokens;
    @Autowired
    private Environment environment;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;
    /** No broker in this test. */
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Parcel parcel;
    private Map<String, String> callers;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM parcel_rating");
        statusHistoryRepository.deleteAll();
        parcelRepository.deleteAll();

        Parcel p = new Parcel();
        p.setTrackingNumber(TRACKING_NO);
        p.setStatus(ParcelStatus.DELIVERED);
        p.setCustomerId(OWNER);
        p.setCourierId(COURIER);
        p.setPickupAddress(new AddressSnapshot("45 Galle Road", null, "Colombo 03", "Colombo", "00300"));
        p.setDropoffAddress(new AddressSnapshot("12 Temple Road", null, "Kandy", "Kandy", "20000"));
        p.setWeightKg(new BigDecimal("2.50"));
        p.setSize(ParcelSize.MEDIUM);
        p.setFee(new BigDecimal("700.00"));
        p.setRecipientName("Sunil Silva");
        p.setRecipientPhone("+94771234567");
        parcel = parcelRepository.saveAndFlush(p);
        StatusHistory created = new StatusHistory(parcel.getId(), ParcelStatus.CREATED, ParcelStatus.CREATED, OWNER, "Parcel order created");
        created.setCreatedAt(Instant.parse("2026-11-13T08:00:00Z"));
        StatusHistory delivered = new StatusHistory(parcel.getId(), ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, COURIER, null);
        delivered.setCreatedAt(Instant.parse("2026-11-13T10:00:00Z"));
        statusHistoryRepository.saveAll(List.of(created, delivered));

        // A second minter has its own random key: same kid, wrong signature.
        DevJwtTokenMinter otherKey = new DevJwtTokenMinter(environment);
        callers = new LinkedHashMap<>();
        callers.put("owner customer", tokens.mintCustomerToken(OWNER));
        callers.put("other customer", tokens.mintCustomerToken(OTHER_CUSTOMER));
        callers.put("assigned courier", tokens.mintCourierToken(COURIER));
        callers.put("other courier", tokens.mintCourierToken(OTHER_COURIER));
        callers.put("dispatcher", tokens.mintDispatcherToken("dispatcher-1"));
        callers.put("admin", tokens.mintAdminToken("admin-1"));
        callers.put("merchant", tokens.mintMerchantToken("merchant-1", "merchant-user-1"));
        callers.put("no token", null);
        callers.put("expired token", tokens.mintToken(OWNER, OWNER + "@example.com", "CUSTOMER", null, Duration.ofMinutes(-5)));
        callers.put("wrong signature", otherKey.mintCustomerToken(OWNER));
        callers.put("not a JWT", "not-a-jwt");
    }

    private int call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    /** Calls the endpoint once per caller and compares with the expected status per caller. */
    private void assertMatrix(String endpoint, java.util.function.Supplier<MockHttpServletRequestBuilder> request,
                              Map<String, Integer> expected) throws Exception {
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (Map.Entry<String, String> caller : callers.entrySet()) {
            if (expected.containsKey(caller.getKey())) {
                actual.put(caller.getKey(), call(request.get(), caller.getValue()));
            }
        }
        assertThat(actual).as(endpoint).containsExactlyEntriesOf(expected);
    }

    private static Map<String, Integer> expect(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("History: owner, assigned courier, dispatcher and admin; nobody else; bad tokens 401")
    void history() throws Exception {
        assertMatrix("GET /history", () -> get("/api/v1/parcels/" + parcel.getId() + "/history"), expect(
                "owner customer", 200, "other customer", 403, "assigned courier", 200, "other courier", 403,
                "dispatcher", 200, "admin", 200, "merchant", 403,
                "no token", 401, "expired token", 401, "wrong signature", 401, "not a JWT", 401));
    }

    @Test
    @DisplayName("Receipt: owner and admin only; bad tokens 401")
    void receipt() throws Exception {
        assertMatrix("GET /receipt", () -> get("/api/v1/parcels/" + parcel.getId() + "/receipt"), expect(
                "owner customer", 200, "other customer", 403, "assigned courier", 403, "other courier", 403,
                "dispatcher", 403, "admin", 200, "merchant", 403,
                "no token", 401, "expired token", 401, "wrong signature", 401, "not a JWT", 401));
    }

    @Test
    @DisplayName("Rating: only the owner may submit; owner and admin may read; bad tokens 401")
    void rating() throws Exception {
        String url = "/api/v1/parcels/" + parcel.getId() + "/rating";
        java.util.function.Supplier<MockHttpServletRequestBuilder> submit =
                () -> post(url).contentType(MediaType.APPLICATION_JSON).content("{\"score\": 4}");

        assertMatrix("POST /rating (not the owner)", submit, expect(
                "other customer", 403, "assigned courier", 403, "other courier", 403,
                "dispatcher", 403, "admin", 403, "merchant", 403,
                "no token", 401, "expired token", 401, "wrong signature", 401, "not a JWT", 401));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcel_rating", Integer.class)).as("nothing stored").isZero();

        assertMatrix("POST /rating (owner)", submit, expect("owner customer", 201));

        assertMatrix("GET /rating", () -> get(url), expect(
                "owner customer", 200, "other customer", 403, "assigned courier", 403, "other courier", 403,
                "dispatcher", 403, "admin", 200, "merchant", 403,
                "no token", 401, "expired token", 401, "wrong signature", 401, "not a JWT", 401));
    }

    @Test
    @DisplayName("Public lookup and stream: open to everyone; any token, even a bad one, is ignored")
    void publicEndpointsIgnoreTokens() throws Exception {
        Map<String, Integer> everyone200 = new LinkedHashMap<>();
        callers.keySet().forEach(caller -> everyone200.put(caller, 200));

        assertMatrix("GET /tracking/{trackingNo}", () -> get("/api/v1/tracking/" + TRACKING_NO), everyone200);
        assertMatrix("GET /tracking/{trackingNo}/stream",
                () -> get("/api/v1/tracking/" + TRACKING_NO + "/stream").accept(MediaType.TEXT_EVENT_STREAM), everyone200);
    }
}
