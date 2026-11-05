package com.swiftdrop.parcel.tracking;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.parcel.ParcelQueryService;
import com.swiftdrop.parcel.parcel.ParcelResponse;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Delivery rating (CU-07, docs/tracking-design.md section 9). Reads parcels only through
 * ParcelQueryService. The customer id always comes from the JWT and the stored parcel.
 */
@Service
class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final ParcelQueryService parcelQueryService;
    private final RatingStore store;
    private final Clock clock;

    @Autowired
    RatingService(ParcelQueryService parcelQueryService, RatingStore store) {
        this(parcelQueryService, store, Clock.systemUTC());
    }

    RatingService(ParcelQueryService parcelQueryService, RatingStore store, Clock clock) {
        this.parcelQueryService = parcelQueryService;
        this.store = store;
        this.clock = clock;
    }

    /** Only the owner customer, only for a DELIVERED parcel, and only once per parcel. */
    RatingResponse rate(String parcelId, CreateRatingRequest request, CurrentUser caller) {
        ParcelResponse parcel = accessibleParcel(parcelId, caller, true);
        if (!isOwner(parcel, caller)) {
            throw RatingException.accessDenied(true);
        }
        if (parcel.status() != ParcelStatus.DELIVERED) {
            throw RatingException.notDelivered(parcel.status());
        }
        RatingResponse rating = new RatingResponse(parcel.id(), request.score(), normalize(request.comment()),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (!store.insert(caller.userId(), rating)) {
            throw RatingException.alreadyRated(parcel.trackingNumber());
        }
        // No comment text in the log: it is free text written by the customer.
        log.info("Delivery rating stored for parcel {} (score {})", parcel.id(), rating.score());
        return rating;
    }

    /** The stored rating, for the owner customer or ADMIN; 404 when not rated yet. */
    RatingResponse get(String parcelId, CurrentUser caller) {
        ParcelResponse parcel = accessibleParcel(parcelId, caller, false);
        if (!caller.isAdmin() && !isOwner(parcel, caller)) {
            throw RatingException.accessDenied(false);
        }
        return store.find(parcel.id()).orElseThrow(() -> RatingException.notRated(parcel.id()));
    }

    /** 404 for an unknown parcel comes from ParcelQueryService; its 403 becomes our JSON 403. */
    private ParcelResponse accessibleParcel(String parcelId, CurrentUser caller, boolean submitting) {
        try {
            return parcelQueryService.getAccessibleParcel(parcelId, caller);
        } catch (AccessDeniedException e) {
            throw RatingException.accessDenied(submitting);
        }
    }

    private static boolean isOwner(ParcelResponse parcel, CurrentUser caller) {
        return caller.isCustomer() && caller.userId() != null && parcel.customerId() != null
                && Objects.equals(caller.userId(), parcel.customerId());
    }

    /** Trims the comment; an empty comment is stored as null. */
    static String normalize(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
