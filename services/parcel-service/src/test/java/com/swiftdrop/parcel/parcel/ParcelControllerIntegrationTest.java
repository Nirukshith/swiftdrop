package com.swiftdrop.parcel.parcel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.address.AddressDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ParcelControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @Autowired
    private ParcelService parcelService;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;

    @BeforeEach
    void setUp() {
        statusHistoryRepository.deleteAll();
        parcelRepository.deleteAll();
        when(pricingQuoteClient.fetchQuote(any(), any(), any(), any()))
                .thenReturn(new BigDecimal("750.00"));
    }

    @Test
    @DisplayName("Customer creates parcel: verifies fee, tracking number, and initial status history")
    void shouldCreateParcelSuccessfully() throws Exception {
        AddressDto pickup = new AddressDto("12 Flower Rd", null, "Colombo", "Colombo", "00700");
        AddressDto dropoff = new AddressDto("45 High Level Rd", null, "Nugegoda", "Colombo", "10250");

        CreateCustomerParcelRequest request = new CreateCustomerParcelRequest(
                pickup,
                dropoff,
                new BigDecimal("2.50"),
                ParcelSize.MEDIUM,
                "Sunil Silva",
                "+94771234567",
                "Fragile"
        );

        var result = mockMvc.perform(post("/api/v1/parcels")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.trackingNumber", matchesRegex("^SD-[0-9]{8}-[A-Za-z0-9]{6}$")))
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andExpect(jsonPath("$.fee", is(750.00)))
                .andExpect(jsonPath("$.customerId", is("usr-cust-1")))
                .andReturn();

        ParcelResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), ParcelResponse.class);

        // Verify status_history written in the same transaction
        List<StatusHistory> history = statusHistoryRepository.findByParcelIdOrderByCreatedAtAsc(response.id());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFromStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(history.get(0).getToStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(history.get(0).getChangedBy()).isEqualTo("usr-cust-1");
    }

    @Test
    @DisplayName("Customer creation rolls back parcel and history when pricing fails")
    void customerCreationRollsBackWhenPricingFails() {
        when(pricingQuoteClient.fetchQuote(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("pricing service unavailable"));

        CreateCustomerParcelRequest request = new CreateCustomerParcelRequest(
                new AddressDto("12 Flower Rd", null, "Colombo", "Colombo", "00700"),
                new AddressDto("45 High Level Rd", null, "Nugegoda", "Colombo", "10250"),
                new BigDecimal("2.50"),
                ParcelSize.MEDIUM,
                "Sunil Silva",
                "+94771234567",
                "Fragile"
        );

        assertThatThrownBy(() -> parcelService.createCustomerParcel("usr-cust-rollback", request))
                .isInstanceOf(IllegalStateException.class);

        assertThat(parcelRepository.count()).isZero();
        assertThat(statusHistoryRepository.count()).isZero();
    }

    @Test
    @DisplayName("Customer B cannot read Customer A's parcel")
    void customerBCannotReadCustomerAParcel() throws Exception {
        // Create parcel owned by Customer A
        Parcel parcel = new Parcel();
        parcel.setTrackingNumber("SD-20261027-CUST01");
        parcel.setStatus(ParcelStatus.CREATED);
        parcel.setCustomerId("usr-cust-A");
        parcel.setPickupAddress(new AddressSnapshot("A", null, "Colombo", "Colombo", "00100"));
        parcel.setDropoffAddress(new AddressSnapshot("B", null, "Colombo", "Colombo", "00200"));
        parcel.setWeightKg(new BigDecimal("1.00"));
        parcel.setSize(ParcelSize.SMALL);
        parcel.setFee(new BigDecimal("500.00"));
        parcel.setRecipientName("Recipient A");
        parcel.setRecipientPhone("+94770000000");
        Parcel saved = parcelRepository.save(parcel);

        // Attempt read as Customer B
        mockMvc.perform(get("/api/v1/parcels/" + saved.getId())
                        .with(user("usr-cust-B").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        // Attempt read as Customer A (succeeds)
        mockMvc.perform(get("/api/v1/parcels/" + saved.getId())
                        .with(user("usr-cust-A").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(saved.getId())));
    }

    @Test
    @DisplayName("Customers cannot read or cancel merchant-owned parcels")
    void customerCannotAccessMerchantParcel() throws Exception {
        Parcel merchantParcel = new Parcel();
        merchantParcel.setTrackingNumber("SD-20261027-MERCH01");
        merchantParcel.setStatus(ParcelStatus.CREATED);
        merchantParcel.setMerchantId("mch-merchant-1");
        merchantParcel.setPickupAddress(new AddressSnapshot("A", null, "Colombo", "Colombo", "00100"));
        merchantParcel.setDropoffAddress(new AddressSnapshot("B", null, "Colombo", "Colombo", "00200"));
        merchantParcel.setWeightKg(new BigDecimal("1.00"));
        merchantParcel.setSize(ParcelSize.SMALL);
        merchantParcel.setFee(new BigDecimal("500.00"));
        merchantParcel.setRecipientName("Merchant Recipient");
        merchantParcel.setRecipientPhone("+94770000000");
        Parcel saved = parcelRepository.save(merchantParcel);

        mockMvc.perform(get("/api/v1/parcels/" + saved.getId())
                        .with(user("usr-cust-1").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/parcels/" + saved.getId() + "/cancel")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelParcelRequest("Unauthorized"))))
                .andExpect(status().isForbidden());

        assertThat(parcelRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(ParcelStatus.CREATED);
    }

    @Test
    @DisplayName("Cancel rules: allowed while CREATED, rejected with 409 once PICKED_UP")
    void testCancellationRules() throws Exception {
        // 1. Parcel in CREATED status
        Parcel parcel = new Parcel();
        parcel.setTrackingNumber("SD-20261027-CNCL01");
        parcel.setStatus(ParcelStatus.CREATED);
        parcel.setCustomerId("usr-cust-1");
        parcel.setPickupAddress(new AddressSnapshot("A", null, "Colombo", "Colombo", "00100"));
        parcel.setDropoffAddress(new AddressSnapshot("B", null, "Colombo", "Colombo", "00200"));
        parcel.setWeightKg(new BigDecimal("1.00"));
        parcel.setSize(ParcelSize.SMALL);
        parcel.setFee(new BigDecimal("500.00"));
        parcel.setRecipientName("Recipient");
        parcel.setRecipientPhone("+94770000000");
        Parcel saved = parcelRepository.save(parcel);

        // Customer cancels while CREATED -> succeeds
        mockMvc.perform(post("/api/v1/parcels/" + saved.getId() + "/cancel")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelParcelRequest("Changed mind"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        mockMvc.perform(post("/api/v1/parcels/" + saved.getId() + "/cancel")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelParcelRequest("Retry"))))
                .andExpect(status().isConflict());

        assertThat(parcelRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(ParcelStatus.CANCELLED);

        // 2. Parcel in PICKED_UP status
        Parcel pickedUpParcel = new Parcel();
        pickedUpParcel.setTrackingNumber("SD-20261027-CNCL02");
        pickedUpParcel.setStatus(ParcelStatus.PICKED_UP);
        pickedUpParcel.setCustomerId("usr-cust-1");
        pickedUpParcel.setCourierId("usr-courier-42");
        pickedUpParcel.setPickupAddress(new AddressSnapshot("A", null, "Colombo", "Colombo", "00100"));
        pickedUpParcel.setDropoffAddress(new AddressSnapshot("B", null, "Colombo", "Colombo", "00200"));
        pickedUpParcel.setWeightKg(new BigDecimal("1.00"));
        pickedUpParcel.setSize(ParcelSize.SMALL);
        pickedUpParcel.setFee(new BigDecimal("500.00"));
        pickedUpParcel.setRecipientName("Recipient");
        pickedUpParcel.setRecipientPhone("+94770000000");
        Parcel savedPickedUp = parcelRepository.save(pickedUpParcel);

        // Customer cancels while PICKED_UP -> fails with 409 Conflict
        mockMvc.perform(post("/api/v1/parcels/" + savedPickedUp.getId() + "/cancel")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelParcelRequest("Too late"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Customer requests fee quote: returns real calculated quote and breakdown")
    void shouldReturnFeeQuoteSuccessfully() throws Exception {
        when(pricingQuoteClient.fetchQuoteDetails("Colombo", "Kandy", new BigDecimal("2.5"), ParcelSize.MEDIUM))
                .thenReturn(new PricingQuoteResponse(
                        new BigDecimal("850.00"),
                        "LKR",
                        new PricingQuoteResponse.QuoteBreakdown(
                                new BigDecimal("350.00"),
                                new BigDecimal("250.00"),
                                new BigDecimal("250.00")
                        ),
                        "Western",
                        "Central"
                ));

        mockMvc.perform(get("/api/v1/parcels/quote")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .param("pickupDistrict", "Colombo")
                        .param("dropoffDistrict", "Kandy")
                        .param("weightKg", "2.5")
                        .param("size", "MEDIUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency", is("LKR")))
                .andExpect(jsonPath("$.totalFee", is(850.00)))
                .andExpect(jsonPath("$.breakdown.dropoffZoneBaseFee", is(350.00)))
                .andExpect(jsonPath("$.breakdown.weightBandCharge", is(250.00)))
                .andExpect(jsonPath("$.breakdown.sizeCharge", is(250.00)))
                .andExpect(jsonPath("$.pickupZone", is("Western")))
                .andExpect(jsonPath("$.dropoffZone", is("Central")));
    }

    @Test
    @DisplayName("Quote request rejected for invalid Sri Lanka district")
    void shouldRejectQuoteForInvalidDistrict() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/quote")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .param("pickupDistrict", "InvalidDistrict")
                        .param("dropoffDistrict", "Colombo")
                        .param("weightKg", "2.5")
                        .param("size", "MEDIUM"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Customer parcel list applies date, tracking query, status, ownership, and stable ordering")
    void customerParcelListFiltersAndOrders() throws Exception {
        saveParcel("SD-20261102-ZZZZZZ", "usr-cust-1", ParcelStatus.CREATED,
                "2026-11-02T10:00:00");
        saveParcel("sd-20261130-ABC123", "usr-cust-1", ParcelStatus.DELIVERED,
                "2026-11-30T23:59:00");
        saveParcel("SD-20261201-ABC123", "usr-cust-1", ParcelStatus.CREATED,
                "2026-12-01T10:00:00");
        saveParcel("SD-20261130-OTHER", "usr-cust-2", ParcelStatus.CREATED,
                "2026-11-30T12:00:00");

        mockMvc.perform(get("/api/v1/parcels")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-30")
                        .param("q", "  abc123  ")
                        .param("status", "DELIVERED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].trackingNumber", is("sd-20261130-ABC123")));

        mockMvc.perform(get("/api/v1/parcels")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .param("from", "2026-11-30")
                        .param("to", "2026-11-30")
                        .param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].trackingNumber", is("sd-20261130-ABC123")));

        mockMvc.perform(get("/api/v1/parcels")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].trackingNumber", is("SD-20261201-ABC123")));

        mockMvc.perform(get("/api/v1/parcels")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].trackingNumber", is("sd-20261130-ABC123")));
    }

    @Test
    @DisplayName("Customer parcel list rejects invalid filter values")
    void customerParcelListRejectsInvalidFilters() throws Exception {
        mockMvc.perform(get("/api/v1/parcels")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .param("from", "2026-12-01")
                        .param("to", "2026-11-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        mockMvc.perform(get("/api/v1/parcels")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .param("from", "2026-11-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        mockMvc.perform(get("/api/v1/parcels")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .param("q", "123456789012345678901234567890123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    private Parcel saveParcel(
            String trackingNumber,
            String customerId,
            ParcelStatus status,
            String createdAt
    ) {
        Parcel parcel = new Parcel();
        parcel.setTrackingNumber(trackingNumber);
        parcel.setStatus(status);
        parcel.setCustomerId(customerId);
        parcel.setPickupAddress(new AddressSnapshot("A", null, "Colombo", "Colombo", "00100"));
        parcel.setDropoffAddress(new AddressSnapshot("B", null, "Colombo", "Colombo", "00200"));
        parcel.setWeightKg(new BigDecimal("1.00"));
        parcel.setSize(ParcelSize.SMALL);
        parcel.setFee(new BigDecimal("500.00"));
        parcel.setRecipientName("Recipient");
        parcel.setRecipientPhone("+94770000000");
        parcel.setCreatedAt(LocalDateTime.parse(createdAt)
                .atZone(ZoneId.of("Asia/Colombo"))
                .toInstant());
        return parcelRepository.save(parcel);
    }

    @Test
    @DisplayName("Quote request rejected for invalid weight (out of bounds)")
    void shouldRejectQuoteForInvalidWeight() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/quote")
                        .with(user("usr-cust-1").roles("CUSTOMER"))
                        .param("pickupDistrict", "Colombo")
                        .param("dropoffDistrict", "Galle")
                        .param("weightKg", "0.0")
                        .param("size", "MEDIUM"))
                .andExpect(status().isBadRequest());
    }
}
