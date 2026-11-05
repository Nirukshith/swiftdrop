package com.swiftdrop.parcel.receipt;

import com.swiftdrop.common.security.CurrentUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Download of a delivered parcel's PDF receipt (CU-05). Generated on request, never stored. */
@RestController
class ReceiptController {

    private final ReceiptService receiptService;

    ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/api/v1/parcels/{id}/receipt")
    ResponseEntity<byte[]> downloadParcelReceipt(@PathVariable String id) {
        ReceiptService.Receipt receipt = receiptService.receipt(id, CurrentUser.require());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(receipt.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(receipt.pdf());
    }
}
