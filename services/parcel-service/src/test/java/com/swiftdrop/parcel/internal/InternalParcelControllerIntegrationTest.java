package com.swiftdrop.parcel.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.address.AddressDto;
import com.swiftdrop.parcel.parcel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InternalParcelControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private ParcelRepository parcelRepository;

    @Autowired
    private MerchantParcelIdempotencyRepository merchantParcelIdempotencyRepository;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @Autowired
    private ParcelService parcelService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        statusHistoryRepository.deleteAll();
        merchantParcelIdempotencyRepository.deleteAll();
        parcelRepository.deleteAll();
    }

    private Parcel createSampleParcel(String trackingNumber, ParcelStatus status, String courierId) {
        Parcel parcel = new Parcel();
        parcel.setTrackingNumber(trackingNumber);
        parcel.setStatus(status);
        parcel.setCourierId(courierId);
        parcel.setPickupAddress(new AddressSnapshot("12 Flower Rd", null, "Colombo", "Colombo", "00700"));
        parcel.setDropoffAddress(new AddressSnapshot("45 High Level Rd", null, "Nugegoda", "Colombo", "10250"));
        parcel.setWeightKg(new BigDecimal("2.50"));
        parcel.setSize(ParcelSize.MEDIUM);
        parcel.setFee(new BigDecimal("750.00"));
        parcel.setRecipientName("Sunil Silva");
        parcel.setRecipientPhone("+94771234567");
        return parcelRepository.save(parcel);
    }

    @Test
    @DisplayName("Assignment: succeeds on unassigned CREATED parcel; returns 409 if already assigned")
    void testCourierAssignment() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-ASSGN1", ParcelStatus.CREATED, null);

        // First assignment succeeds
        ParcelAssignmentRequest request = new ParcelAssignmentRequest("usr-courier-42", "dispatcher-1");
        mockMvc.perform(put("/internal/v1/parcels/" + parcel.getId() + "/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId", is("usr-courier-42")));

        // Duplicate assignment returns 409 Conflict
        ParcelAssignmentRequest duplicate = new ParcelAssignmentRequest("usr-courier-99", "dispatcher-1");
        mockMvc.perform(put("/internal/v1/parcels/" + parcel.getId() + "/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Transitions through the API: full physical lifecycle CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED")
    void testFullDeliveryLifecycle() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-LIFE01", ParcelStatus.CREATED, "usr-courier-42");

        // 1. CREATED -> PICKED_UP
        ParcelStatusUpdateRequest step1 = new ParcelStatusUpdateRequest(
                ParcelStatus.PICKED_UP,
                ParcelStatus.CREATED,
                "usr-courier-42",
                null,
                null,
                "retry-key-1"
        );
        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PICKED_UP")));

        // 2. PICKED_UP -> IN_TRANSIT
        ParcelStatusUpdateRequest step2 = new ParcelStatusUpdateRequest(
                ParcelStatus.IN_TRANSIT,
                ParcelStatus.PICKED_UP,
                "usr-courier-42",
                null,
                null,
                "retry-key-2"
        );
        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_TRANSIT")));

        // 3. IN_TRANSIT -> OUT_FOR_DELIVERY
        ParcelStatusUpdateRequest step3 = new ParcelStatusUpdateRequest(
                ParcelStatus.OUT_FOR_DELIVERY,
                ParcelStatus.IN_TRANSIT,
                "usr-courier-42",
                null,
                null,
                "retry-key-3"
        );
        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OUT_FOR_DELIVERY")));

        // 4. OUT_FOR_DELIVERY -> DELIVERED (with proof)
        DeliveryProof proof = new DeliveryProof("Sunil Silva", "/data/proofs/test.jpg");
        ParcelStatusUpdateRequest step4 = new ParcelStatusUpdateRequest(
                ParcelStatus.DELIVERED,
                ParcelStatus.OUT_FOR_DELIVERY,
                "usr-courier-42",
                null,
                proof,
                "retry-key-4"
        );
        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DELIVERED")))
                .andExpect(jsonPath("$.proof.recipientName", is("Sunil Silva")));
    }

    @Test
    @DisplayName("Failure and Re-queue (Decision 005): FAILED -> CREATED clears courier and increments attemptCount")
    void testFailureAndRequeue() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-FAIL01", ParcelStatus.OUT_FOR_DELIVERY, "usr-courier-42");

        // 1. Mark FAILED with reason
        ParcelStatusUpdateRequest failStep = new ParcelStatusUpdateRequest(
                ParcelStatus.FAILED,
                ParcelStatus.OUT_FOR_DELIVERY,
                "usr-courier-42",
                "RECIPIENT_UNAVAILABLE",
                null,
                null
        );
        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failStep)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.failureReason", is("RECIPIENT_UNAVAILABLE")));

        // 2. Dispatcher re-queues FAILED parcel back to CREATED
        ParcelStatusUpdateRequest requeueStep = new ParcelStatusUpdateRequest(
                ParcelStatus.CREATED,
                ParcelStatus.FAILED,
                "dispatcher-1",
                "Re-queued for 2nd attempt",
                null,
                null
        );
        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requeueStep)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andExpect(jsonPath("$.courierId", nullValue())) // Courier is cleared!
                .andExpect(jsonPath("$.attemptCount", is(1))); // Attempt count incremented!
    }

    @Test
    @DisplayName("Status PATCH with mismatched expectedCurrentStatus returns 409 Conflict")
    void testExpectedStatusConflict() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-CNFL01", ParcelStatus.PICKED_UP, "usr-courier-42");

        // Request expects CREATED but actual status is PICKED_UP
        ParcelStatusUpdateRequest conflictReq = new ParcelStatusUpdateRequest(
                ParcelStatus.IN_TRANSIT,
                ParcelStatus.CREATED,
                "usr-courier-42",
                null,
                null,
                null
        );
        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Idempotent retry: re-sending the same status returns 200 OK")
    void testIdempotentRetry() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-RETR01", ParcelStatus.PICKED_UP, "usr-courier-42");

        // Request requests PICKED_UP when parcel is already PICKED_UP
        ParcelStatusUpdateRequest retryReq = new ParcelStatusUpdateRequest(
                ParcelStatus.PICKED_UP,
                ParcelStatus.PICKED_UP,
                "usr-courier-42",
                null,
                null,
                null
        );
        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retryReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PICKED_UP")));
    }

    @Test
    @DisplayName("Stale idempotent retry: mismatched expected status returns 409 even when target was already reached")
    void testStaleIdempotentRetryReturnsConflict() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-STALE1", ParcelStatus.PICKED_UP, "usr-courier-42");

        ParcelStatusUpdateRequest staleRetry = new ParcelStatusUpdateRequest(
                ParcelStatus.PICKED_UP,
                ParcelStatus.CREATED,
                "usr-courier-42",
                null,
                null,
                null
        );

        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staleRetry)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Illegal status transition returns 409")
    void testIllegalStatusTransitionReturnsConflict() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-ILLEGAL", ParcelStatus.CREATED, "usr-courier-42");

        ParcelStatusUpdateRequest illegalTransition = new ParcelStatusUpdateRequest(
                ParcelStatus.DELIVERED,
                ParcelStatus.CREATED,
                "usr-courier-42",
                null,
                null,
                null
        );

        mockMvc.perform(patch("/internal/v1/parcels/" + parcel.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalTransition)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Internal queries: list unassigned parcels and courier deliveries")
    void testInternalQueries() throws Exception {
        createSampleParcel("SD-20261027-UNAS01", ParcelStatus.CREATED, null);
        createSampleParcel("SD-20261027-COUR01", ParcelStatus.OUT_FOR_DELIVERY, "usr-courier-99");

        // Query unassigned
        mockMvc.perform(get("/internal/v1/parcels?unassigned=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trackingNumber", is("SD-20261027-UNAS01")));

        // Query by courier
        mockMvc.perform(get("/internal/v1/parcels?courierId=usr-courier-99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trackingNumber", is("SD-20261027-COUR01")));
    }

    @Test
    @DisplayName("Merchant parcel creation: POST /internal/v1/parcels returns 201 Created")
    void testMerchantParcelCreation() throws Exception {
        AddressDto pickup = new AddressDto("100 Port Rd", null, "Colombo", "Colombo", "00100");
        AddressDto dropoff = new AddressDto("200 Kandy Rd", null, "Kandy", "Kandy", "20000");

        InternalCreateParcelRequest request = new InternalCreateParcelRequest(
                "mch-merchant-1",
                "EXT-9921",
                pickup,
                dropoff,
                new BigDecimal("3.00"),
                ParcelSize.LARGE,
                new BigDecimal("1200.00"),
                "Devinda Fernando",
                "+94779998877"
        );

        mockMvc.perform(post("/internal/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.merchantId", is("mch-merchant-1")))
                .andExpect(jsonPath("$.fee", is(1200.00)))
                .andExpect(jsonPath("$.status", is("CREATED")));
    }

    @Test
    @DisplayName("Merchant parcel creation replays the durable result for the same key and payload")
    void testMerchantParcelIdempotentReplay() throws Exception {
        InternalCreateParcelRequest request = merchantRequest("mch-merchant-1", "EXT-REPLAY");
        InternalCreateParcelRequest equivalentRequest = new InternalCreateParcelRequest(
                request.merchantId(),
                request.externalReference(),
                request.pickupAddress(),
                request.dropoffAddress(),
                new BigDecimal("3.0"),
                request.size(),
                new BigDecimal("1200.0"),
                request.recipientName(),
                request.recipientPhone()
        );

        String first = mockMvc.perform(post("/internal/v1/parcels")
                        .header("Idempotency-Key", "merchant-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(equivalentRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String replay = mockMvc.perform(post("/internal/v1/parcels")
                        .header("Idempotency-Key", "merchant-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ParcelResponse firstResponse = objectMapper.readValue(first, ParcelResponse.class);
        ParcelResponse replayResponse = objectMapper.readValue(replay, ParcelResponse.class);
        assertThat(replayResponse.id()).isEqualTo(firstResponse.id());
        assertThat(replayResponse.trackingNumber()).isEqualTo(firstResponse.trackingNumber());
        assertThat(parcelRepository.count()).isEqualTo(1);
        assertThat(merchantParcelIdempotencyRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Merchant idempotency reservation rolls back when parcel persistence fails")
    void testMerchantIdempotencyRollsBackWithParcelFailure() {
        doThrow(new IllegalStateException("simulated parcel persistence failure"))
                .when(parcelRepository).save(org.mockito.ArgumentMatchers.any(Parcel.class));

        assertThatThrownBy(() -> parcelService.createMerchantParcel(
                merchantRequest("mch-merchant-rollback", "EXT-ROLLBACK"),
                "rollback-key"
        )).isInstanceOf(DataAccessException.class);

        assertThat(parcelRepository.count()).isZero();
        assertThat(merchantParcelIdempotencyRepository.count()).isZero();
    }

    @Test
    @DisplayName("Merchant parcel idempotency rejects the same key with a different payload")
    void testMerchantParcelIdempotencyConflict() throws Exception {
        InternalCreateParcelRequest first = merchantRequest("mch-merchant-1", "EXT-FIRST");
        InternalCreateParcelRequest different = merchantRequest("mch-merchant-1", "EXT-DIFFERENT");

        mockMvc.perform(post("/internal/v1/parcels")
                        .header("Idempotency-Key", "merchant-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/internal/v1/parcels")
                        .header("Idempotency-Key", "merchant-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(different)))
                .andExpect(status().isConflict());

        assertThat(parcelRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Merchant parcel idempotency keys are isolated between merchants")
    void testMerchantParcelIdempotencyMerchantIsolation() throws Exception {
        InternalCreateParcelRequest merchantA = merchantRequest("mch-merchant-A", "EXT-A");
        InternalCreateParcelRequest merchantB = merchantRequest("mch-merchant-B", "EXT-B");

        mockMvc.perform(post("/internal/v1/parcels")
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(merchantA)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/internal/v1/parcels")
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(merchantB)))
                .andExpect(status().isCreated());

        assertThat(parcelRepository.count()).isEqualTo(2);
        assertThat(merchantParcelIdempotencyRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Concurrent merchant retries create one parcel for one idempotency key")
    void testConcurrentMerchantParcelRetries() throws Exception {
        InternalCreateParcelRequest request = merchantRequest("mch-merchant-concurrent", "EXT-CONCURRENT");
        String body = objectMapper.writeValueAsString(request);
        Callable<String> create = () -> mockMvc.perform(post("/internal/v1/parcels")
                        .header("Idempotency-Key", "concurrent-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var executor = Executors.newFixedThreadPool(2);
        try {
            List<String> responses = executor.invokeAll(List.of(create, create)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .toList();
            ParcelResponse first = objectMapper.readValue(responses.get(0), ParcelResponse.class);
            ParcelResponse second = objectMapper.readValue(responses.get(1), ParcelResponse.class);
            assertThat(second.id()).isEqualTo(first.id());
            assertThat(parcelRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private InternalCreateParcelRequest merchantRequest(String merchantId, String externalReference) {
        return new InternalCreateParcelRequest(
                merchantId,
                externalReference,
                new AddressDto("100 Port Rd", null, "Colombo", "Colombo", "00100"),
                new AddressDto("200 Kandy Rd", null, "Kandy", "Kandy", "20000"),
                new BigDecimal("3.00"),
                ParcelSize.LARGE,
                new BigDecimal("1200.00"),
                "Devinda Fernando",
                "+94779998877"
        );
    }

    @Test
    @DisplayName("Optimistic-lock conflict: concurrent update on stale version triggers 409 Conflict")
    void testOptimisticLockConflict() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-LOCK01", ParcelStatus.CREATED, null);

        // Fetch parcel and update in background (incrementing version from 0 to 1)
        Parcel concurrentlyUpdated = parcelRepository.findById(parcel.getId()).orElseThrow();
        concurrentlyUpdated.setNotes("First concurrent update");
        parcelRepository.saveAndFlush(concurrentlyUpdated);

        // Now attempt to update using stale entity (version = 0)
        parcel.setNotes("Stale concurrent update");
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.OptimisticLockingFailureException.class,
                () -> parcelRepository.saveAndFlush(parcel)
        );
    }

    @Test
    @DisplayName("Pre-pickup reassignment: succeeds with reassign=true query param, updates courier and sequence")
    void testPrePickupReassignmentWithQueryParam() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-REASSGN1", ParcelStatus.CREATED, null);

        // 1. Initial assignment to courier A
        mockMvc.perform(put("/internal/v1/parcels/" + parcel.getId() + "/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ParcelAssignmentRequest("usr-courier-A", "dispatcher-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId", is("usr-courier-A")))
                .andExpect(jsonPath("$.sequence", is(2)));

        // 2. Reassignment to courier B with ?reassign=true
        mockMvc.perform(put("/internal/v1/parcels/" + parcel.getId() + "/assignment?reassign=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ParcelAssignmentRequest("usr-courier-B", "dispatcher-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId", is("usr-courier-B")))
                .andExpect(jsonPath("$.sequence", is(3)));

        // Verify database state
        Parcel updated = parcelRepository.findById(parcel.getId()).orElseThrow();
        assertThat(updated.getCourierId()).isEqualTo("usr-courier-B");
        assertThat(updated.getSequence()).isEqualTo(3L);

        // Verify status history audit
        var history = statusHistoryRepository.findByParcelIdOrderByCreatedAtAsc(parcel.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(1).getReason()).contains("Reassigned to courier usr-courier-B from usr-courier-A");
    }

    @Test
    @DisplayName("Pre-pickup reassignment: succeeds with body reassign=true flag")
    void testPrePickupReassignmentWithBodyFlag() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-REASSGN2", ParcelStatus.CREATED, "usr-courier-A");

        mockMvc.perform(put("/internal/v1/parcels/" + parcel.getId() + "/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ParcelAssignmentRequest("usr-courier-C", "dispatcher-2", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId", is("usr-courier-C")));

        Parcel updated = parcelRepository.findById(parcel.getId()).orElseThrow();
        assertThat(updated.getCourierId()).isEqualTo("usr-courier-C");
    }

    @Test
    @DisplayName("Same-courier reassignment: idempotent retry returns 200 without changing sequence")
    void testSameCourierReassignmentIsIdempotent() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-REASSGN3", ParcelStatus.CREATED, "usr-courier-A");
        long initialSequence = parcel.getSequence();

        mockMvc.perform(put("/internal/v1/parcels/" + parcel.getId() + "/assignment?reassign=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ParcelAssignmentRequest("usr-courier-A", "dispatcher-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId", is("usr-courier-A")))
                .andExpect(jsonPath("$.sequence", is((int) initialSequence)));

        Parcel updated = parcelRepository.findById(parcel.getId()).orElseThrow();
        assertThat(updated.getSequence()).isEqualTo(initialSequence);
    }

    @Test
    @DisplayName("Concurrent assignment allows one writer and rejects the stale writer")
    void testConcurrentAssignmentDoesNotOverwriteCourier() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-CONCUR1", ParcelStatus.CREATED, null);
        Callable<Boolean> assignA = () -> assignSucceeded(parcel.getId(), "usr-courier-A");
        Callable<Boolean> assignB = () -> assignSucceeded(parcel.getId(), "usr-courier-B");

        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Boolean> outcomes = executor.invokeAll(List.of(assignA, assignB)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .toList();

            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
            assertThat(parcelRepository.findById(parcel.getId()).orElseThrow().getCourierId())
                    .isIn("usr-courier-A", "usr-courier-B");
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean assignSucceeded(String parcelId, String courierId) {
        try {
            parcelService.assignCourier(parcelId, courierId, "dispatcher-1");
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Test
    @DisplayName("Reassignment post-pickup: returns 409 Conflict when parcel is already PICKED_UP")
    void testReassignmentRejectedPostPickup() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-REASSGN4", ParcelStatus.PICKED_UP, "usr-courier-A");

        mockMvc.perform(put("/internal/v1/parcels/" + parcel.getId() + "/assignment?reassign=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ParcelAssignmentRequest("usr-courier-B", "dispatcher-1"))))
                .andExpect(status().isConflict());

        // Database remains unchanged
        Parcel unchanged = parcelRepository.findById(parcel.getId()).orElseThrow();
        assertThat(unchanged.getCourierId()).isEqualTo("usr-courier-A");
    }

    @Test
    @DisplayName("Reassignment: returns 404 Not Found for non-existent parcel")
    void testReassignmentParcelNotFound() throws Exception {
        mockMvc.perform(put("/internal/v1/parcels/00000000-0000-0000-0000-000000000000/assignment?reassign=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ParcelAssignmentRequest("usr-courier-B", "dispatcher-1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Reassignment: returns 400 Bad Request if courierId is blank")
    void testReassignmentBlankCourier() throws Exception {
        Parcel parcel = createSampleParcel("SD-20261027-REASSGN5", ParcelStatus.CREATED, "usr-courier-A");

        mockMvc.perform(put("/internal/v1/parcels/" + parcel.getId() + "/assignment?reassign=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ParcelAssignmentRequest("  ", "dispatcher-1"))))
                .andExpect(status().isBadRequest());
    }
}
