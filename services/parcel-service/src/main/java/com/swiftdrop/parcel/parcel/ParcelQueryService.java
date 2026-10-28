package com.swiftdrop.parcel.parcel;

import com.swiftdrop.common.security.CurrentUser;

import java.util.List;
import java.util.Optional;

/**
 * Read-only access to parcels for other packages in parcel-service (M2 tracking and receipt),
 * so they never touch the parcel entities or repositories directly (SRS 8.2 package isolation).
 * See docs/tracking-design.md section 7.
 */
public interface ParcelQueryService {

    /**
     * Public tracking lookup. No access check: callers must mask the result before
     * returning it to anonymous users.
     */
    Optional<ParcelResponse> findByTrackingNumber(String trackingNumber);

    /**
     * Parcel for an authenticated caller, using the access rule of GET /api/v1/parcels/{id}
     * in parcel.yaml: owner customer, assigned courier, DISPATCHER or ADMIN.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if the parcel does not exist
     * @throws org.springframework.security.access.AccessDeniedException if the caller may not see it
     */
    ParcelResponse getAccessibleParcel(String parcelId, CurrentUser caller);

    /** Status history of a parcel, oldest first, exactly as stored (no access check). */
    List<StatusHistoryView> findStatusHistory(String parcelId);
}
