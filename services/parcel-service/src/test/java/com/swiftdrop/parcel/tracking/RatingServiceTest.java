package com.swiftdrop.parcel.tracking;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.parcel.ParcelQueryService;
import com.swiftdrop.parcel.parcel.ParcelResponse;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RatingServiceTest {

    private static final String PARCEL_ID = "parcel-1";
    private static final String OWNER = "customer-1";
    private static final Instant NOW = Instant.parse("2026-10-30T04:30:00Z");

    private final ParcelQueryService parcelQueryService = mock(ParcelQueryService.class);
    private final RatingStore store = mock(RatingStore.class);
    private final RatingService service = new RatingService(parcelQueryService, store, Clock.fixed(NOW, ZoneOffset.UTC));

    private static CurrentUser user(String id, String role) {
        return new CurrentUser(id, null, role, Set.of(role), null, Set.of("ROLE_" + role));
    }

    private static ParcelResponse parcel(ParcelStatus status, String customerId) {
        return new ParcelResponse(PARCEL_ID, "SD-20261028-RAT001", status, customerId, null, "courier-1",
                null, null, null, null, null, 0, null, "Sunil Silva", "+94771234567", null, null, 5L, NOW, NOW);
    }

    private void given(ParcelStatus status, CurrentUser caller) {
        when(parcelQueryService.getAccessibleParcel(PARCEL_ID, caller)).thenReturn(parcel(status, OWNER));
    }

    private static CreateRatingRequest request(int score, String comment) {
        return new CreateRatingRequest(score, comment);
    }

    // ------------------------------------------------------------------ submit

    @Test
    @DisplayName("The owner rates a delivered parcel; the customer id stored is the caller's, from the token")
    void ownerRates() {
        CurrentUser owner = user(OWNER, "CUSTOMER");
        given(ParcelStatus.DELIVERED, owner);
        when(store.insert(anyString(), any())).thenReturn(true);

        RatingResponse rating = service.rate(PARCEL_ID, request(5, "  On time and polite  "), owner);

        assertThat(rating).isEqualTo(new RatingResponse(PARCEL_ID, 5, "On time and polite", NOW));
        ArgumentCaptor<RatingResponse> stored = ArgumentCaptor.forClass(RatingResponse.class);
        verify(store).insert(eq(OWNER), stored.capture());
        assertThat(stored.getValue()).isEqualTo(rating);
    }

    @Test
    @DisplayName("A blank comment is stored as no comment")
    void blankComment() {
        assertThat(RatingService.normalize(null)).isNull();
        assertThat(RatingService.normalize("   ")).isNull();
        assertThat(RatingService.normalize(" ok ")).isEqualTo("ok");
    }

    @Test
    @DisplayName("Every status other than DELIVERED gets 409 PARCEL_NOT_DELIVERED and nothing is stored")
    void notDelivered() {
        CurrentUser owner = user(OWNER, "CUSTOMER");
        for (ParcelStatus status : ParcelStatus.values()) {
            if (status == ParcelStatus.DELIVERED) {
                continue;
            }
            given(status, owner);
            assertThatThrownBy(() -> service.rate(PARCEL_ID, request(4, null), owner))
                    .isInstanceOfSatisfying(RatingException.class, e -> {
                        assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.code()).isEqualTo("PARCEL_NOT_DELIVERED");
                        assertThat(e.getMessage()).endsWith("current status is " + status);
                    });
        }
        verify(store, never()).insert(anyString(), any());
    }

    @Test
    @DisplayName("A second rating of the same parcel gets 409 RATING_ALREADY_EXISTS")
    void duplicate() {
        CurrentUser owner = user(OWNER, "CUSTOMER");
        given(ParcelStatus.DELIVERED, owner);
        when(store.insert(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.rate(PARCEL_ID, request(3, null), owner))
                .isInstanceOfSatisfying(RatingException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.code()).isEqualTo("RATING_ALREADY_EXISTS");
                    assertThat(e.getMessage()).isEqualTo("Parcel SD-20261028-RAT001 has already been rated");
                });
    }

    @Test
    @DisplayName("ADMIN, DISPATCHER and the assigned courier can see the parcel but may not rate it")
    void onlyOwnerCustomerRates() {
        for (CurrentUser caller : List.of(user("admin-1", "ADMIN"), user("dispatcher-1", "DISPATCHER"), user("courier-1", "COURIER"))) {
            given(ParcelStatus.DELIVERED, caller);
            assertThatThrownBy(() -> service.rate(PARCEL_ID, request(5, null), caller))
                    .isInstanceOfSatisfying(RatingException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verify(store, never()).insert(anyString(), any());
    }

    @Test
    @DisplayName("Another customer is denied (403 from ParcelQueryService becomes the rating 403)")
    void otherCustomer() {
        CurrentUser other = user("customer-2", "CUSTOMER");
        when(parcelQueryService.getAccessibleParcel(PARCEL_ID, other)).thenThrow(new AccessDeniedException("denied"));

        assertThatThrownBy(() -> service.rate(PARCEL_ID, request(5, null), other))
                .isInstanceOfSatisfying(RatingException.class, e -> assertThat(e.code()).isEqualTo("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("A merchant parcel has no customer, so no customer can rate it")
    void merchantParcel() {
        CurrentUser customer = user(OWNER, "CUSTOMER");
        when(parcelQueryService.getAccessibleParcel(PARCEL_ID, customer)).thenReturn(parcel(ParcelStatus.DELIVERED, null));

        assertThatThrownBy(() -> service.rate(PARCEL_ID, request(5, null), customer))
                .isInstanceOfSatisfying(RatingException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Unknown parcel: the 404 from ParcelQueryService is passed on")
    void unknownParcel() {
        CurrentUser owner = user(OWNER, "CUSTOMER");
        when(parcelQueryService.getAccessibleParcel(eq("missing"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found with id: missing"));

        assertThatThrownBy(() -> service.rate("missing", request(5, null), owner)).isInstanceOf(ResponseStatusException.class);
    }

    // ------------------------------------------------------------------ read

    @Test
    @DisplayName("Owner and ADMIN read the stored rating; 404 RESOURCE_NOT_FOUND when not rated yet")
    void readRating() {
        RatingResponse stored = new RatingResponse(PARCEL_ID, 4, null, NOW);
        for (CurrentUser caller : List.of(user(OWNER, "CUSTOMER"), user("admin-1", "ADMIN"))) {
            given(ParcelStatus.DELIVERED, caller);
            when(store.find(PARCEL_ID)).thenReturn(Optional.of(stored));
            assertThat(service.get(PARCEL_ID, caller)).isEqualTo(stored);

            when(store.find(PARCEL_ID)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.get(PARCEL_ID, caller))
                    .isInstanceOfSatisfying(RatingException.class, e -> {
                        assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(e.code()).isEqualTo("RESOURCE_NOT_FOUND");
                    });
        }
    }

    @Test
    @DisplayName("DISPATCHER and the courier may not read the rating")
    void othersMayNotRead() {
        for (CurrentUser caller : List.of(user("dispatcher-1", "DISPATCHER"), user("courier-1", "COURIER"))) {
            given(ParcelStatus.DELIVERED, caller);
            assertThatThrownBy(() -> service.get(PARCEL_ID, caller))
                    .isInstanceOfSatisfying(RatingException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verify(store, never()).find(anyString());
    }
}
