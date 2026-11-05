package com.swiftdrop.parcel.receipt;

/**
 * The caller may not download this parcel's receipt (403 ACCESS_DENIED). A separate type from
 * Spring's AccessDeniedException so the receipt handler can always answer with a JSON body,
 * even when the client asked for application/pdf.
 */
class ReceiptAccessDeniedException extends RuntimeException {

    static final String MESSAGE = "You do not have access to this parcel's receipt";

    ReceiptAccessDeniedException() {
        super(MESSAGE);
    }
}
