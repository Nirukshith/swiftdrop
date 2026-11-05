package com.swiftdrop.parcel.receipt;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.parcel.ParcelQueryService;
import com.swiftdrop.parcel.parcel.ParcelResponse;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import com.swiftdrop.parcel.parcel.StatusHistoryView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

/**
 * PDF receipt for a delivered parcel (CU-05, parcel-read.yaml downloadParcelReceipt).
 * Reads parcels only through ParcelQueryService; ownership comes from the stored parcel and the
 * JWT, never from the request.
 */
@Service
class ReceiptService {

    private final ParcelQueryService parcelQueryService;
    private final ReceiptPdfRenderer renderer;
    private final Clock clock;

    @Autowired
    ReceiptService(ParcelQueryService parcelQueryService, ReceiptPdfRenderer renderer) {
        this(parcelQueryService, renderer, Clock.systemUTC());
    }

    ReceiptService(ParcelQueryService parcelQueryService, ReceiptPdfRenderer renderer, Clock clock) {
        this.parcelQueryService = parcelQueryService;
        this.renderer = renderer;
        this.clock = clock;
    }

    /**
     * 404 for an unknown parcel and 403 for callers who may not see it come from
     * ParcelQueryService. On top of that only the owner customer and ADMIN get a receipt
     * (403 for the assigned courier and DISPATCHER), and only for a DELIVERED parcel (409).
     */
    Receipt receipt(String parcelId, CurrentUser caller) {
        ParcelResponse parcel;
        try {
            parcel = parcelQueryService.getAccessibleParcel(parcelId, caller);
        } catch (AccessDeniedException e) {
            throw new ReceiptAccessDeniedException();
        }
        if (!mayDownload(parcel, caller)) {
            throw new ReceiptAccessDeniedException();
        }
        if (parcel.status() != ParcelStatus.DELIVERED) {
            throw new ParcelNotDeliveredException(parcel.status());
        }
        Instant deliveredAt = parcelQueryService.findStatusHistory(parcel.id()).stream()
                .filter(entry -> entry.toStatus() == ParcelStatus.DELIVERED)
                .map(StatusHistoryView::occurredAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(parcel.updatedAt());
        byte[] pdf = renderer.render(ReceiptData.of(parcel, deliveredAt, clock.instant()));
        return new Receipt(fileName(parcel.trackingNumber()), pdf);
    }

    static String fileName(String trackingNumber) {
        return "receipt-" + trackingNumber + ".pdf";
    }

    private static boolean mayDownload(ParcelResponse parcel, CurrentUser caller) {
        if (caller.isAdmin()) {
            return true;
        }
        return caller.isCustomer() && caller.userId() != null && Objects.equals(caller.userId(), parcel.customerId());
    }

    record Receipt(String fileName, byte[] pdf) {
    }
}
