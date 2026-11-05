package com.swiftdrop.parcel.receipt;

/** Building the PDF failed. Mapped to a generic 500 without parcel details. */
class ReceiptGenerationException extends RuntimeException {

    ReceiptGenerationException(Throwable cause) {
        super("Receipt PDF could not be generated", cause);
    }
}
