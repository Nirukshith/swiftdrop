package com.swiftdrop.parcel.receipt;

import com.swiftdrop.parcel.parcel.ParcelStatus;

/** The parcel has no receipt yet because it is not DELIVERED (409 PARCEL_NOT_DELIVERED). */
class ParcelNotDeliveredException extends RuntimeException {

    ParcelNotDeliveredException(ParcelStatus status) {
        super("A receipt is only available for delivered parcels; current status is " + status);
    }
}
