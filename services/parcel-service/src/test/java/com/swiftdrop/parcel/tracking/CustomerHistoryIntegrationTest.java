package com.swiftdrop.parcel.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Week 5 (Issue #174): the customer history and receipt flow with real signed
 * bearer tokens, through parcel-service's own security chain (dev profile key).
 *
 * The history list is M1's GET /api/v1/parcels (parcel.yaml); M2's history page reads it. This test
 * pins what the page relies on (owner scoping, paging metadata, status filter) without changing it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class CustomerHistoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String OWNER = "c1a2b3c4-0000-4000-8000-0000000000e1";
    private static final String OTHER = "c1a2b3c4-0000-4000-8000-0000000000e2";
    private static final String COURIER = "c1a2b3c4-0000-4000-8000-0000000000e3";
    private static final DateTimeFormatter RECEIPT_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.of("Asia/Colombo"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private StatusHistoryRepository statusHistoryRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DevJwtTokenMinter tokens;

    @MockBean
    private PricingQuoteClient pricingQuoteClient;
    /** No broker in this test: status events are not published. */
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final List<String> ownerIds = new ArrayList<>();
    private final List<String> ownerDelivered = new ArrayList<>();

    /** Owner: 23 parcels (every third DELIVERED). Another customer: 5 parcels. */
    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM parcel_rating");
        statusHistoryRepository.deleteAll();
        parcelRepository.deleteAll();
        ownerIds.clear();
        ownerDelivered.clear();
        for (int i = 1; i <= 23; i++) {
            ParcelStatus status = i % 3 == 1 ? ParcelStatus.DELIVERED : ParcelStatus.IN_TRANSIT;
            Parcel p = save(String.format("SD-20261119-OWN%03d", i), OWNER, status);
            ownerIds.add(p.getId());
            if (status == ParcelStatus.DELIVERED) {
                ownerDelivered.add(p.getId());
            }
        }
        for (int i = 1; i <= 5; i++) {
            save(String.format("SD-20261119-OTH%03d", i), OTHER, ParcelStatus.DELIVERED);
        }
    }

    private Parcel save(String trackingNumber, String customerId, ParcelStatus status) {
        Parcel p = new Parcel();
        p.setTrackingNumber(trackingNumber);
        p.setStatus(status);
        p.setCustomerId(customerId);
        p.setCourierId(COURIER);
        p.setPickupAddress(new AddressSnapshot("45 Galle Road", null, "Colombo 03", "Colombo", "00300"));
        p.setDropoffAddress(new AddressSnapshot("12 Temple Road", "Flat 2", "Kandy", "Kandy", "20000"));
        p.setWeightKg(new BigDecimal("2.50"));
        p.setSize(ParcelSize.MEDIUM);
        p.setFee(new BigDecimal("1250.00"));
        p.setRecipientName("Sunil Silva");
        p.setRecipientPhone("+94771234567");
        p.setNotes("Gate code 4321");
        Parcel saved = parcelRepository.saveAndFlush(p);
        StatusHistory created = new StatusHistory(saved.getId(), ParcelStatus.CREATED, ParcelStatus.CREATED, customerId, "Parcel order created");
        created.setCreatedAt(Instant.parse("2026-11-18T08:00:00Z"));
        List<StatusHistory> rows = new ArrayList<>(List.of(created));
        if (status == ParcelStatus.DELIVERED) {
            StatusHistory delivered = new StatusHistory(saved.getId(), ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, COURIER, null);
            delivered.setCreatedAt(Instant.parse("2026-11-19T10:15:00Z"));
            rows.add(delivered);
        }
        statusHistoryRepository.saveAll(rows);
        return saved;
    }

    private MvcResult call(String path, String token) throws Exception {
        var request = get(path);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode list(String query, String token) throws Exception {
        MvcResult result = call("/api/v1/parcels" + query, token);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("content").forEach(parcel -> ids.add(parcel.get("id").asText()));
        return ids;
    }

    // ------------------------------------------------------------------ history list (M1's endpoint, as the page uses it)

    @Test
    @DisplayName("History pages of 10: correct metadata, every own parcel exactly once, nobody else's")
    void paginationCoversOnlyOwnParcels() throws Exception {
        String owner = tokens.mintCustomerToken(OWNER);
        Set<String> seen = new HashSet<>();
        for (int page = 0; page < 3; page++) {
            JsonNode body = list("?page=" + page + "&size=10", owner);
            assertThat(body.get("totalElements").asLong()).isEqualTo(23);
            assertThat(body.get("totalPages").asInt()).isEqualTo(3);
            assertThat(body.get("page").asInt()).isEqualTo(page);
            assertThat(body.get("size").asInt()).isEqualTo(10);
            assertThat(body.get("content")).hasSize(page < 2 ? 10 : 3);
            body.get("content").forEach(parcel -> assertThat(parcel.get("customerId").asText()).isEqualTo(OWNER));
            List<String> pageIds = ids(body);
            assertThat(seen).doesNotContainAnyElementsOf(pageIds);
            seen.addAll(pageIds);
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(ownerIds);

        JsonNode beyond = list("?page=3&size=10", owner);
        assertThat(beyond.get("content")).isEmpty();
        assertThat(beyond.get("totalElements").asLong()).isEqualTo(23);
    }

    @Test
    @DisplayName("Status filter returns only the caller's parcels in that status")
    void statusFilter() throws Exception {
        JsonNode delivered = list("?status=DELIVERED&size=50", tokens.mintCustomerToken(OWNER));

        assertThat(delivered.get("totalElements").asLong()).isEqualTo(ownerDelivered.size());
        assertThat(ids(delivered)).containsExactlyInAnyOrderElementsOf(ownerDelivered);
        delivered.get("content").forEach(parcel -> assertThat(parcel.get("status").asText()).isEqualTo("DELIVERED"));
    }

    @Test
    @DisplayName("Another customer sees only their own parcels; a courier token lists none")
    void ownershipIsTakenFromTheToken() throws Exception {
        JsonNode other = list("?size=50", tokens.mintCustomerToken(OTHER));
        assertThat(other.get("totalElements").asLong()).isEqualTo(5);
        assertThat(ids(other)).doesNotContainAnyElementsOf(ownerIds);

        // A customerId in the query is not a supported parameter and does not widen the result.
        JsonNode spoofed = list("?size=50&customerId=" + OWNER, tokens.mintCustomerToken(OTHER));
        assertThat(ids(spoofed)).doesNotContainAnyElementsOf(ownerIds);

        assertThat(list("?size=50", tokens.mintCourierToken(COURIER)).get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("History without a valid token: 401")
    void historyNeedsAValidToken() throws Exception {
        assertThat(call("/api/v1/parcels", null).getResponse().getStatus()).isEqualTo(401);
        assertThat(call("/api/v1/parcels", "not-a-real-token").getResponse().getStatus()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ receipt with signed tokens

    @Test
    @DisplayName("Receipt with a signed token: owner gets the PDF of the stored parcel; others 403; undelivered 409; no token 401")
    void receiptWithRealTokens() throws Exception {
        String delivered = ownerDelivered.get(0);
        String inTransit = ownerIds.stream().filter(id -> !ownerDelivered.contains(id)).findFirst().orElseThrow();
        Parcel stored = parcelRepository.findById(delivered).orElseThrow();
        String path = "/api/v1/parcels/" + delivered + "/receipt";

        MvcResult ok = call(path, tokens.mintCustomerToken(OWNER));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(ok.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(ok.getResponse().getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"receipt-" + stored.getTrackingNumber() + ".pdf\"");
        String text;
        try (PdfReader reader = new PdfReader(ok.getResponse().getContentAsByteArray())) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            text = new PdfTextExtractor(reader).getTextFromPage(1);
        }
        assertThat(text).contains(stored.getTrackingNumber(), stored.getId(), "Delivered", "Sunil Silva",
                "12 Temple Road, Flat 2, Kandy, Kandy, 20000", "Medium", "2.5 kg", "LKR 1,250.00",
                // Delivery time from the stored DELIVERED history row (2026-11-19T10:15Z = 15:45 in Colombo)
                "Delivered " + RECEIPT_TIME.format(Instant.parse("2026-11-19T10:15:00Z")));
        assertThat(text).doesNotContain("+9477", "Gate code", COURIER);

        assertThat(call(path, tokens.mintCustomerToken(OTHER)).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(path, tokens.mintCourierToken(COURIER)).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(path, tokens.mintAdminToken("admin-1")).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(path, null).getResponse().getStatus()).isEqualTo(401);

        MvcResult notYet = call("/api/v1/parcels/" + inTransit + "/receipt", tokens.mintCustomerToken(OWNER));
        assertThat(notYet.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(notYet.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("PARCEL_NOT_DELIVERED");
    }
}
