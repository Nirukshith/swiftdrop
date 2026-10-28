package com.swiftdrop.parcel.parcel;

import com.swiftdrop.common.Roles;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.address.AddressDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ParcelQueryServiceImpl.class)
@Testcontainers
class ParcelQueryServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String OWNER = "c1a2b3c4-0000-4000-8000-000000000001";
    private static final String OTHER_CUSTOMER = "c1a2b3c4-0000-4000-8000-000000000002";
    private static final String COURIER = "c1a2b3c4-0000-4000-8000-000000000003";

    @Autowired
    private ParcelQueryService queryService;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    private Parcel parcel;

    @BeforeEach
    void setUp() {
        statusHistoryRepository.deleteAll();
        parcelRepository.deleteAll();

        Parcel p = new Parcel();
        p.setTrackingNumber("SD-20261028-QRY123");
        p.setStatus(ParcelStatus.PICKED_UP);
        p.setCustomerId(OWNER);
        p.setCourierId(COURIER);
        p.setPickupAddress(AddressSnapshot.from(new AddressDto("45 Galle Road", null, "Colombo 03", "Colombo", "00300")));
        p.setDropoffAddress(AddressSnapshot.from(new AddressDto("12 Temple Road", null, "Kandy", "Kandy", "20000")));
        p.setWeightKg(new BigDecimal("2.50"));
        p.setSize(ParcelSize.MEDIUM);
        p.setFee(new BigDecimal("750.00"));
        p.setRecipientName("Sunil Silva");
        p.setRecipientPhone("+94771234567");
        parcel = parcelRepository.saveAndFlush(p);

        StatusHistory created = new StatusHistory(parcel.getId(), ParcelStatus.CREATED, ParcelStatus.CREATED, OWNER, "Parcel order created");
        created.setCreatedAt(Instant.parse("2026-10-28T08:00:00Z"));
        StatusHistory pickedUp = new StatusHistory(parcel.getId(), ParcelStatus.CREATED, ParcelStatus.PICKED_UP, COURIER, null);
        pickedUp.setCreatedAt(Instant.parse("2026-10-28T09:00:00Z"));
        // Saved out of order on purpose: the query must sort by time.
        statusHistoryRepository.saveAll(List.of(pickedUp, created));
    }

    private static CurrentUser user(String id, String role) {
        return new CurrentUser(id, null, role, Set.of(role), null, Set.of("ROLE_" + role));
    }

    @Test
    @DisplayName("findByTrackingNumber returns the parcel, or empty for unknown numbers")
    void findByTrackingNumber() {
        assertThat(queryService.findByTrackingNumber("SD-20261028-QRY123"))
                .hasValueSatisfying(found -> {
                    assertThat(found.id()).isEqualTo(parcel.getId());
                    assertThat(found.status()).isEqualTo(ParcelStatus.PICKED_UP);
                    assertThat(found.dropoffAddress().district()).isEqualTo("Kandy");
                });
        assertThat(queryService.findByTrackingNumber("SD-20990101-NOPE00")).isEmpty();
    }

    @Test
    @DisplayName("Owner customer, assigned courier, dispatcher and admin may read the parcel")
    void allowedCallers() {
        for (CurrentUser caller : List.of(
                user(OWNER, Roles.CUSTOMER),
                user(COURIER, Roles.COURIER),
                user("dispatcher-1", Roles.DISPATCHER),
                user("admin-1", Roles.ADMIN))) {
            assertThat(queryService.getAccessibleParcel(parcel.getId(), caller).trackingNumber())
                    .as(caller.role())
                    .isEqualTo("SD-20261028-QRY123");
        }
    }

    @Test
    @DisplayName("Another customer, another courier, a merchant or no caller are denied")
    void deniedCallers() {
        for (CurrentUser caller : List.of(
                user(OTHER_CUSTOMER, Roles.CUSTOMER),
                user("courier-2", Roles.COURIER),
                user(OWNER, Roles.MERCHANT))) {
            assertThatThrownBy(() -> queryService.getAccessibleParcel(parcel.getId(), caller))
                    .as(caller.role())
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> queryService.getAccessibleParcel(parcel.getId(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Unknown parcel id gives 404")
    void unknownParcel() {
        assertThatThrownBy(() -> queryService.getAccessibleParcel("does-not-exist", user("admin-1", Roles.ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("findStatusHistory returns rows oldest first, as stored")
    void statusHistory() {
        List<StatusHistoryView> history = queryService.findStatusHistory(parcel.getId());

        assertThat(history).extracting(StatusHistoryView::toStatus)
                .containsExactly(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
        assertThat(history.get(0).fromStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(history.get(0).reason()).isEqualTo("Parcel order created");
        assertThat(history.get(1).occurredAt()).isEqualTo(Instant.parse("2026-10-28T09:00:00Z"));
        assertThat(queryService.findStatusHistory("does-not-exist")).isEmpty();
    }
}
