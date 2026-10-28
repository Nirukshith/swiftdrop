package com.swiftdrop.parcel.tracking;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.parcel.ParcelQueryService;
import com.swiftdrop.parcel.parcel.ParcelResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

/** Read side for tracking and history. Reads parcels only through ParcelQueryService. */
@Service
class TrackingService {

    static final Pattern TRACKING_NUMBER = Pattern.compile("^SD-\\d{8}-[A-Z0-9]{6}$");

    private final ParcelQueryService parcelQueryService;

    TrackingService(ParcelQueryService parcelQueryService) {
        this.parcelQueryService = parcelQueryService;
    }

    Optional<PublicTrackingResponse> publicTracking(String trackingNumber) {
        if (trackingNumber == null || !TRACKING_NUMBER.matcher(trackingNumber).matches()) {
            return Optional.empty();
        }
        return parcelQueryService.findByTrackingNumber(trackingNumber)
                .map(parcel -> TrackingMapper.toPublic(parcel, parcelQueryService.findStatusHistory(parcel.id())));
    }

    /** 404 and 403 come from ParcelQueryService (owner customer, assigned courier, DISPATCHER, ADMIN). */
    ParcelHistoryResponse history(String parcelId, CurrentUser caller) {
        ParcelResponse parcel = parcelQueryService.getAccessibleParcel(parcelId, caller);
        return TrackingMapper.toHistory(parcel, parcelQueryService.findStatusHistory(parcel.id()));
    }
}
