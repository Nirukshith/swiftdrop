package com.swiftdrop.parcel.receipt;

/**
 * The parcel does not exist (404 RESOURCE_NOT_FOUND). A receipt-specific type, like
 * ReceiptAccessDeniedException, so the receipt handler answers with a JSON body even when the
 * client asked for application/pdf; a handler without an explicit content type sends no body then.
 */
class ReceiptParcelNotFoundException extends RuntimeException {

    static final String MESSAGE = "Parcel not found";

    ReceiptParcelNotFoundException() {
        super(MESSAGE);
    }
}
