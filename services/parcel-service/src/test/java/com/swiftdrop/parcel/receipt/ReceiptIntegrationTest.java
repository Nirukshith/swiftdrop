package com.swiftdrop.parcel.receipt;

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
import org.openpdf.text.pdf.PdfReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Receipt download against a real Postgres. The parcel is moved to DELIVERED through M1's
 * status workflow (state machine and status_history), not by writing the status directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReceiptIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String TRACKING_NO = "SD-20261028-RCP001";
    private static final String OWNER = "c1a2b3c4-0000-4000-8000-0000000000b1";
    private static final String COURIER = "c1a2b3c4-0000-4000-8000-0000000000b3";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;

    /** No broker in this test: status events are not published. */
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @SpyBean
    private ReceiptPdfRenderer renderer;

    private Parcel parcel;

    @BeforeEach
    void setUp() {
        statusHistoryRepository.deleteAll();
        parcelRepository.deleteAll();

        Parcel p = new Parcel();
        p.setTrackingNumber(TRACKING_NO);
        p.setStatus(ParcelStatus.CREATED);
        p.setCustomerId(OWNER);
        p.setCourierId(COURIER);
        p.setPickupAddress(new AddressSnapshot("45 Galle Road", null, "Colombo 03", "Colombo", "00300"));
        p.setDropoffAddress(new AddressSnapshot("12 Temple Road", "Flat 2", "Kandy", "Kandy", "20000"));
        p.setWeightKg(new BigDecimal("2.50"));
        p.setSize(ParcelSize.MEDIUM);
        p.setFee(new BigDecimal("750.00"));
        p.setRecipientName("Sunil Silva");
        p.setRecipientPhone("+94771234567");
        p.setNotes("Gate code 4321");
        parcel = parcelRepository.saveAndFlush(p);

        StatusHistory created = new StatusHistory(parcel.getId(), ParcelStatus.CREATED, ParcelStatus.CREATED, OWNER, "Parcel order created");
        created.setCreatedAt(Instant.parse("2026-10-28T08:00:00Z"));
        statusHistoryRepository.saveAndFlush(created);
    }

    private void moveTo(ParcelStatus from, ParcelStatus to) throws Exception {
        String proof = to == ParcelStatus.DELIVERED
                ? ", \"proof\": {\"recipientName\": \"Sunil Silva\", \"proofPhotoPath\": \"proofs/p1.jpg\"}" : "";
        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"" + to + "\", \"expectedCurrentStatus\": \"" + from
                                + "\", \"courierId\": \"" + COURIER + "\"" + proof + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(to.name())));
    }

    private void deliver() throws Exception {
        moveTo(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
        moveTo(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
        moveTo(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
        moveTo(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED);
    }

    private String receiptUrl() {
        return "/api/v1/parcels/" + parcel.getId() + "/receipt";
    }

    @Test
    @DisplayName("Owner downloads a valid PDF of the delivered parcel, as an attachment named after the tracking number")
    void ownerDownloadsReceipt() throws Exception {
        deliver();

        MvcResult result = mockMvc.perform(get(receiptUrl())
                        .accept(MediaType.APPLICATION_PDF)
                        .with(user(OWNER).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"receipt-" + TRACKING_NO + ".pdf\""))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
        }
        String text = ReceiptPdfRendererTest.extractText(pdf);
        assertThat(text).contains(TRACKING_NO, parcel.getId(), "Delivered", "Sunil Silva",
                "12 Temple Road, Flat 2, Kandy, Kandy, 20000", "45 Galle Road, Colombo 03, Colombo, 00300",
                "Medium", "2.5 kg", "LKR 750.00",
                "Created " + ReceiptPdfRenderer.date(parcelRepository.findById(parcel.getId()).orElseThrow().getCreatedAt()));
        // Not on a receipt: phone, notes, proof photo, courier
        assertThat(text).doesNotContain("+9477", "Gate code", "proofs/p1.jpg", COURIER);
    }

    @Test
    @DisplayName("ADMIN may download the receipt")
    void adminDownloadsReceipt() throws Exception {
        deliver();

        mockMvc.perform(get(receiptUrl()).with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Another customer gets 403 and no PDF")
    void otherCustomerDenied() throws Exception {
        deliver();

        mockMvc.perform(get(receiptUrl()).accept(MediaType.APPLICATION_PDF).with(user("someone-else").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")))
                .andExpect(jsonPath("$.message", is("You do not have access to this parcel's receipt")));
    }

    @Test
    @DisplayName("The assigned courier and DISPATCHER get 403 for the receipt")
    void courierAndDispatcherDenied() throws Exception {
        deliver();

        mockMvc.perform(get(receiptUrl()).with(user(COURIER).roles("COURIER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
        mockMvc.perform(get(receiptUrl()).with(user("dispatcher-1").roles("DISPATCHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("A parcel that is not delivered yet gets 409 PARCEL_NOT_DELIVERED as JSON, even when a PDF was asked for")
    void notDeliveredYet() throws Exception {
        moveTo(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);

        mockMvc.perform(get(receiptUrl()).accept(MediaType.APPLICATION_PDF).with(user(OWNER).roles("CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", is("PARCEL_NOT_DELIVERED")))
                .andExpect(jsonPath("$.message", is("A receipt is only available for delivered parcels; current status is PICKED_UP")))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Unknown parcel gives 404 RESOURCE_NOT_FOUND")
    void unknownParcel() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/00000000-0000-4000-8000-000000000000/receipt").with(user(OWNER).roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Parcel not found")));
    }

    @Test
    @DisplayName("Anonymous callers are rejected and get no PDF")
    void anonymousRejected() throws Exception {
        deliver();

        // 403 today: parcel-service's SecurityConfig has no authentication entry point yet,
        // so Spring Security answers 403 instead of the contract's 401 (same as /history).
        MvcResult result = mockMvc.perform(get(receiptUrl())).andReturn();
        assertThat(result.getResponse().getStatus()).isIn(401, 403);
        assertThat(result.getResponse().getContentType()).isNotEqualTo(MediaType.APPLICATION_PDF_VALUE);
    }

    @Test
    @DisplayName("If PDF generation fails the caller gets a generic 500 without parcel details")
    void generationFailure() throws Exception {
        deliver();
        doThrow(new ReceiptGenerationException(new IllegalStateException("font missing for Sunil Silva")))
                .when(renderer).render(any());

        String body = mockMvc.perform(get(receiptUrl()).with(user(OWNER).roles("CUSTOMER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is("RECEIPT_GENERATION_FAILED")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Sunil", "font missing", TRACKING_NO, parcel.getId());
    }
}
