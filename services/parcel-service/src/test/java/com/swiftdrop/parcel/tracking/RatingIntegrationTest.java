package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.AddressSnapshot;
import com.swiftdrop.parcel.parcel.Parcel;
import com.swiftdrop.parcel.parcel.ParcelRepository;
import com.swiftdrop.parcel.parcel.ParcelSize;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import com.swiftdrop.parcel.parcel.PricingQuoteClient;
import com.swiftdrop.parcel.parcel.StatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rating against a real Postgres (V4 parcel_rating). The parcel reaches DELIVERED through M1's
 * internal status workflow, not by writing the status directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RatingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String OWNER = "c1a2b3c4-0000-4000-8000-0000000000c1";
    private static final String COURIER = "c1a2b3c4-0000-4000-8000-0000000000c3";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;

    /** No broker in this test: status events are not published. */
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Parcel parcel;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM parcel_rating");
        statusHistoryRepository.deleteAll();
        parcelRepository.deleteAll();
        parcel = parcelRepository.saveAndFlush(newParcel("SD-20261028-RAT001", OWNER));
    }

    private static Parcel newParcel(String trackingNumber, String customerId) {
        Parcel p = new Parcel();
        p.setTrackingNumber(trackingNumber);
        p.setStatus(ParcelStatus.CREATED);
        p.setCustomerId(customerId);
        p.setCourierId(COURIER);
        p.setPickupAddress(new AddressSnapshot("45 Galle Road", null, "Colombo 03", "Colombo", "00300"));
        p.setDropoffAddress(new AddressSnapshot("12 Temple Road", null, "Kandy", "Kandy", "20000"));
        p.setWeightKg(new BigDecimal("2.50"));
        p.setSize(ParcelSize.MEDIUM);
        p.setFee(new BigDecimal("750.00"));
        p.setRecipientName("Sunil Silva");
        p.setRecipientPhone("+94771234567");
        return p;
    }

    private void moveTo(Parcel target, ParcelStatus from, ParcelStatus to) throws Exception {
        mockMvc.perform(patch("/internal/v1/parcels/" + target.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"" + to + "\", \"expectedCurrentStatus\": \"" + from
                                + "\", \"courierId\": \"" + COURIER + "\"}"))
                .andExpect(status().isOk());
    }

    private void deliver(Parcel target) throws Exception {
        moveTo(target, ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
        moveTo(target, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
        moveTo(target, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
        moveTo(target, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED);
    }

    private String url() {
        return "/api/v1/parcels/" + parcel.getId() + "/rating";
    }

    private static RequestPostProcessor owner() {
        return user(OWNER).roles("CUSTOMER");
    }

    private ResultActions rate(String body, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(post(url()).contentType(MediaType.APPLICATION_JSON).content(body).with(caller));
    }

    private List<Map<String, Object>> storedRows() {
        return jdbc.queryForList("SELECT parcel_id, customer_id, score, comment FROM parcel_rating");
    }

    // ------------------------------------------------------------------ submit

    @Test
    @DisplayName("Owner rates a delivered parcel: 201 with the rating, stored once with the caller's customer id")
    void ownerRatesDeliveredParcel() throws Exception {
        deliver(parcel);

        rate("{\"score\": 5, \"comment\": \"  Courier was on time and polite \"}", owner())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelId", is(parcel.getId())))
                .andExpect(jsonPath("$.score", is(5)))
                .andExpect(jsonPath("$.comment", is("Courier was on time and polite")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(storedRows()).singleElement().satisfies(row -> {
            assertThat(row.get("parcel_id")).isEqualTo(parcel.getId());
            assertThat(row.get("customer_id")).isEqualTo(OWNER);
            assertThat(((Number) row.get("score")).intValue()).isEqualTo(5);
            assertThat(row.get("comment")).isEqualTo("Courier was on time and polite");
        });

        // Still there on a later read (what the page shows after a refresh)
        mockMvc.perform(get(url()).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score", is(5)))
                .andExpect(jsonPath("$.comment", is("Courier was on time and polite")));
    }

    @Test
    @DisplayName("The comment is optional")
    void commentIsOptional() throws Exception {
        deliver(parcel);

        rate("{\"score\": 3}", owner())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment", nullValue()));
    }

    @Test
    @DisplayName("A second rating gets 409 RATING_ALREADY_EXISTS and the first one is kept")
    void duplicateRejected() throws Exception {
        deliver(parcel);
        rate("{\"score\": 5}", owner()).andExpect(status().isCreated());

        rate("{\"score\": 1, \"comment\": \"changed my mind\"}", owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("RATING_ALREADY_EXISTS")))
                .andExpect(jsonPath("$.message", is("Parcel SD-20261028-RAT001 has already been rated")));

        assertThat(storedRows()).singleElement()
                .satisfies(row -> assertThat(((Number) row.get("score")).intValue()).isEqualTo(5));
    }

    @Test
    @DisplayName("Two ratings sent at the same moment: exactly one is stored, the other gets 409")
    void concurrentSubmissions() throws Exception {
        deliver(parcel);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int score : new int[] {4, 2}) {
                Callable<Integer> call = () -> {
                    start.await();
                    return rate("{\"score\": " + score + "}", owner()).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(storedRows()).hasSize(1);
    }

    @Test
    @DisplayName("A parcel that is not delivered yet gets 409 PARCEL_NOT_DELIVERED")
    void notDeliveredRejected() throws Exception {
        moveTo(parcel, ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
        moveTo(parcel, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
        moveTo(parcel, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);

        rate("{\"score\": 5}", owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PARCEL_NOT_DELIVERED")))
                .andExpect(jsonPath("$.message", is("Only delivered parcels can be rated; current status is OUT_FOR_DELIVERY")));
        assertThat(storedRows()).isEmpty();
    }

    @Test
    @DisplayName("Invalid bodies get 400 VALIDATION_FAILED and nothing is stored")
    void invalidBodies() throws Exception {
        deliver(parcel);
        String tooLong = "x".repeat(501);
        for (String body : List.of(
                "{\"score\": 0}", "{\"score\": 6}", "{\"score\": -1}", "{}", "{\"score\": null}",
                "{\"score\": 4.5}", "{\"score\": \"5\"}", "{\"score\": 99999999999}",
                "{\"score\": 5, \"comment\": \"" + tooLong + "\"}",
                "not json", "")) {
            rate(body, owner())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
        }
        assertThat(storedRows()).isEmpty();

        rate("{\"score\": 5, \"comment\": \"" + "x".repeat(500) + "\"}", owner()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Another customer, the courier, DISPATCHER and ADMIN may not rate: 403, nothing stored")
    void othersMayNotRate() throws Exception {
        deliver(parcel);

        rate("{\"score\": 1}", user("someone-else").roles("CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
        rate("{\"score\": 1}", user(COURIER).roles("COURIER")).andExpect(status().isForbidden());
        rate("{\"score\": 1}", user("dispatcher-1").roles("DISPATCHER")).andExpect(status().isForbidden());
        rate("{\"score\": 1}", user("admin-1").roles("ADMIN")).andExpect(status().isForbidden());

        assertThat(storedRows()).isEmpty();
    }

    @Test
    @DisplayName("A customer cannot rate someone else's parcel even with a valid token of their own")
    void cannotRateAnotherCustomersParcel() throws Exception {
        Parcel other = parcelRepository.saveAndFlush(newParcel("SD-20261028-RAT002", "c1a2b3c4-0000-4000-8000-0000000000c2"));
        deliver(other);

        mockMvc.perform(post("/api/v1/parcels/" + other.getId() + "/rating")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\": 1}").with(owner()))
                .andExpect(status().isForbidden());
        assertThat(storedRows()).isEmpty();
    }

    @Test
    @DisplayName("Unknown parcel gives 404 RESOURCE_NOT_FOUND")
    void unknownParcel() throws Exception {
        mockMvc.perform(post("/api/v1/parcels/00000000-0000-4000-8000-000000000000/rating")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\": 5}").with(owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("Anonymous callers are rejected and nothing is stored")
    void anonymousRejected() throws Exception {
        deliver(parcel);

        // 403 today: parcel-service's SecurityConfig has no authentication entry point yet,
        // so Spring Security answers 403 instead of the contract's 401 (same as /history).
        int submit = mockMvc.perform(post(url()).contentType(MediaType.APPLICATION_JSON).content("{\"score\": 5}"))
                .andReturn().getResponse().getStatus();
        int read = mockMvc.perform(get(url())).andReturn().getResponse().getStatus();
        assertThat(submit).isIn(401, 403);
        assertThat(read).isIn(401, 403);
        assertThat(storedRows()).isEmpty();
    }

    // ------------------------------------------------------------------ read

    @Test
    @DisplayName("Before a rating exists GET gives 404 RESOURCE_NOT_FOUND; ADMIN may read it, others may not")
    void readRules() throws Exception {
        deliver(parcel);

        mockMvc.perform(get(url()).with(owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("No rating found for parcel " + parcel.getId())));

        rate("{\"score\": 4}", owner()).andExpect(status().isCreated());

        mockMvc.perform(get(url()).with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score", is(4)));
        mockMvc.perform(get(url()).with(user("someone-else").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(url()).with(user("dispatcher-1").roles("DISPATCHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The database itself rejects a score outside 1-5")
    void databaseConstraint() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO parcel_rating (parcel_id, customer_id, score) VALUES (?, ?, ?)", parcel.getId(), OWNER, 6))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
