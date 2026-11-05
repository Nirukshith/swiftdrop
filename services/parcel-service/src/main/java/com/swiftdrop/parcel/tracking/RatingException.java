package com.swiftdrop.parcel.tracking;

import org.springframework.http.HttpStatus;

/** A refused rating request, with the HTTP status and contract error code to answer with. */
class RatingException extends RuntimeException {

    static final String ACCESS_DENIED = "ACCESS_DENIED";
    static final String NOT_FOUND = "RESOURCE_NOT_FOUND";
    static final String NOT_DELIVERED = "PARCEL_NOT_DELIVERED";
    static final String ALREADY_RATED = "RATING_ALREADY_EXISTS";

    private final HttpStatus status;
    private final String code;

    private RatingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static RatingException accessDenied(boolean submitting) {
        return new RatingException(HttpStatus.FORBIDDEN, ACCESS_DENIED, submitting
                ? "Only the customer who sent this parcel can rate it"
                : "You do not have access to this parcel's rating");
    }

    static RatingException notRated(String parcelId) {
        return new RatingException(HttpStatus.NOT_FOUND, NOT_FOUND, "No rating found for parcel " + parcelId);
    }

    static RatingException notDelivered(Object status) {
        return new RatingException(HttpStatus.CONFLICT, NOT_DELIVERED,
                "Only delivered parcels can be rated; current status is " + status);
    }

    static RatingException alreadyRated(String trackingNumber) {
        return new RatingException(HttpStatus.CONFLICT, ALREADY_RATED,
                "Parcel " + trackingNumber + " has already been rated");
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }
}
