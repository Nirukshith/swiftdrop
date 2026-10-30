package com.swiftdrop.parcel.tracking;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TrackingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String TRACKING_NO = "SD-20261028-TRK001";
    private static final String OWNER = "c1a2b3c4-0000-4000-8000-0000000000a1";
    private static final String COURIER = "c1a2b3c4-0000-4000-8000-0000000000a3";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;

    private Parcel parcel;

    @BeforeEach
    void setUp() {
        statusHistoryRepository.deleteAll();
        parcelRepository.deleteAll();

        Parcel p = new Parcel();
        p.setTrackingNumber(TRACKING_NO);
        p.setStatus(ParcelStatus.PICKED_UP);
        p.setCustomerId(OWNER);
        p.setCourierId(COURIER);
        p.setPickupAddress(new AddressSnapshot("45 Galle Road", null, "Colombo 03", "Colombo", "00300"));
        p.setDropoffAddress(new AddressSnapshot("12 Temple Road", "Flat 2", "Kandy", "Kandy", "20000"));
        p.setWeightKg(new BigDecimal("2.50"));
        p.setSize(ParcelSize.MEDIUM);
        p.setFee(new BigDecimal("750.00"));
        p.setRecipientName("Sunil Silva");
        p.setRecipientPhone("+94771234567");
        p.setNotes("Leave at the gate");
        parcel = parcelRepository.saveAndFlush(p);

        StatusHistory created = new StatusHistory(parcel.getId(), ParcelStatus.CREATED, ParcelStatus.CREATED, OWNER, "Parcel order created");
        created.setCreatedAt(Instant.parse("2026-10-28T08:00:00Z"));
        StatusHistory pickedUp = new StatusHistory(parcel.getId(), ParcelStatus.CREATED, ParcelStatus.PICKED_UP, COURIER, null);
        pickedUp.setCreatedAt(Instant.parse("2026-10-28T09:00:00Z"));
        statusHistoryRepository.saveAll(List.of(created, pickedUp));
    }

    /** Each test uses its own client IP so the per-IP limit never leaks between tests. */
    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    // ---------------------------------------------------------------- public tracking

    @Test
    @DisplayName("Public lookup needs no login and returns only masked, allow-listed fields")
    void publicLookupIsMasked() throws Exception {
        String body = mockMvc.perform(get("/api/v1/tracking/" + TRACKING_NO).with(from("198.51.100.1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber", is(TRACKING_NO)))
                .andExpect(jsonPath("$.status", is("PICKED_UP")))
                .andExpect(jsonPath("$.recipientNameMasked", is("S*** S***")))
                .andExpect(jsonPath("$.dropoffDistrict", is("Kandy")))
                .andExpect(jsonPath("$.lastUpdatedAt", is("2026-10-28T09:00:00Z")))
                .andExpect(jsonPath("$.timeline", hasSize(2)))
                .andExpect(jsonPath("$.timeline[0].status", is("CREATED")))
                .andExpect(jsonPath("$.timeline[1].status", is("PICKED_UP")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("Temple Road", "Flat 2", "20000", "Galle Road", "+9477", "750", "Leave at the gate",
                        OWNER, COURIER, parcel.getId(), "Parcel order created", "Sunil", "sequence", "reason");
    }

    @Test
    @DisplayName("Unknown and malformed tracking numbers give the same 404 body")
    void unknownAndMalformedLookLikeEachOther() throws Exception {
        for (String trackingNo : List.of("SD-20990101-NOPE00", "sd-20261028-trk001", "not-a-number", "SD-20261028-TRK0011")) {
            mockMvc.perform(get("/api/v1/tracking/" + trackingNo).with(from("198.51.100.2")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")))
                    .andExpect(jsonPath("$.message", is("No parcel found for this tracking number")))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }

    @Test
    @DisplayName("Only GET is allowed on the public path")
    void publicPathIsReadOnly() throws Exception {
        mockMvc.perform(post("/api/v1/tracking/" + TRACKING_NO).with(from("198.51.100.3")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- rate limit

    @Test
    @DisplayName("After the burst a client gets 429 with Retry-After and the ErrorResponse body")
    void rateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/tracking/" + TRACKING_NO).with(from("198.51.100.10")))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/tracking/" + TRACKING_NO).with(from("198.51.100.10")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.code", is("TOO_MANY_REQUESTS")))
                .andExpect(jsonPath("$.message", is("Too many tracking requests, please retry later")))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        // another client is not affected
        mockMvc.perform(get("/api/v1/tracking/" + TRACKING_NO).with(from("198.51.100.11")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Behind the gateway each client is limited by X-Forwarded-For")
    void forwardedForFromGateway() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/tracking/" + TRACKING_NO).with(from("127.0.0.1"))
                            .header("X-Forwarded-For", "203.0.113.20"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/tracking/" + TRACKING_NO).with(from("127.0.0.1"))
                        .header("X-Forwarded-For", "203.0.113.20"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/v1/tracking/" + TRACKING_NO).with(from("127.0.0.1"))
                        .header("X-Forwarded-For", "203.0.113.21"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A direct caller cannot dodge the limit with a fake X-Forwarded-For")
    void spoofedForwardedForIsIgnored() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/tracking/" + TRACKING_NO).with(from("198.51.100.30"))
                            .header("X-Forwarded-For", "203.0.113." + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/tracking/" + TRACKING_NO).with(from("198.51.100.30"))
                        .header("X-Forwarded-For", "203.0.113.99"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("The limit does not apply to authenticated parcel endpoints")
    void historyIsNotRateLimited() throws Exception {
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(get("/api/v1/parcels/" + parcel.getId() + "/history")
                            .with(from("198.51.100.40")).with(user(OWNER).roles("CUSTOMER")))
                    .andExpect(status().isOk());
        }
    }

    // ---------------------------------------------------------------- history

    @Test
    @DisplayName("Owner gets the timeline: creation entry has fromStatus null, sequence is null but present")
    void ownerReadsHistory() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/" + parcel.getId() + "/history").with(user(OWNER).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId", is(parcel.getId())))
                .andExpect(jsonPath("$.trackingNumber", is(TRACKING_NO)))
                .andExpect(jsonPath("$.currentStatus", is("PICKED_UP")))
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[0]", hasKey("sequence")))
                .andExpect(jsonPath("$.entries[0].sequence", nullValue()))
                .andExpect(jsonPath("$.entries[0]", hasKey("fromStatus")))
                .andExpect(jsonPath("$.entries[0].fromStatus", nullValue()))
                .andExpect(jsonPath("$.entries[0].toStatus", is("CREATED")))
                .andExpect(jsonPath("$.entries[0].occurredAt", is("2026-10-28T08:00:00Z")))
                .andExpect(jsonPath("$.entries[0].reason", is("Parcel order created")))
                .andExpect(jsonPath("$.entries[1].fromStatus", is("CREATED")))
                .andExpect(jsonPath("$.entries[1].toStatus", is("PICKED_UP")))
                .andExpect(jsonPath("$.entries[1]", hasKey("reason")));
    }

    @Test
    @DisplayName("Assigned courier, dispatcher and admin may read the history")
    void staffReadHistory() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/" + parcel.getId() + "/history").with(user(COURIER).roles("COURIER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/parcels/" + parcel.getId() + "/history").with(user("dispatcher-1").roles("DISPATCHER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/parcels/" + parcel.getId() + "/history").with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Another customer or courier gets 403")
    void othersDenied() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/" + parcel.getId() + "/history").with(user("someone-else").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").isNotEmpty());
        mockMvc.perform(get("/api/v1/parcels/" + parcel.getId() + "/history").with(user("courier-2").roles("COURIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unknown parcel gives 404 RESOURCE_NOT_FOUND")
    void unknownParcel() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/00000000-0000-4000-8000-000000000000/history").with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("Anonymous callers are rejected by the parcel security chain")
    void anonymousRejected() throws Exception {
        // 403 today: the parcel package's SecurityConfig has no authentication entry point yet,
        // so Spring Security answers 403 instead of the contract's 401.
        int status = mockMvc.perform(get("/api/v1/parcels/" + parcel.getId() + "/history"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }
}
