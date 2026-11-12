package com.swiftdrop.integration.merchant;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MerchantBulkService {
    private final MerchantCsv csv;
    private final MerchantBulkStore store;
    private final MerchantOrderService orders;

    public MerchantBulkService(MerchantCsv csv, MerchantBulkStore store, MerchantOrderService orders) {
        this.csv = csv; this.store = store; this.orders = orders;
    }

    public MerchantBulkResponse submit(UUID merchant, String client, String key, byte[] bytes) {
        if (bytes.length > MerchantCsv.MAX_BYTES) throw new ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "CSV exceeds 5 MiB");
        // Compare existing content hashes first; validate the whole new file before parcel operations.
        var upload = store.initialize(merchant, client, key, hash(bytes), () -> csv.parse(bytes));
        if (!upload.fresh()) return store.report(merchant, upload.id());
        for (var row : upload.rows()) {
            if (!row.errors().isEmpty()) continue;
            String rowKey = store.rowKey(merchant, key, row.rowNumber());
            if (!store.claim(merchant, upload.id(), row, rowKey)) continue;
            try {
                var result = orders.createFromBulk(merchant, rowKey, row.request());
                store.finish(upload.id(), row.rowNumber(), "SUCCEEDED",
                        new MerchantBulkResponse.Result(row.rowNumber(), true, result.body(), List.of()));
            } catch (MerchantOrderService.PricingUnavailableException exception) {
                store.failBeforeParcel(merchant, upload.id(), row, "Pricing unavailable; no parcel was requested. Correct the cause before submitting this row with a new upload key.");
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 422) {
                    store.failBeforeParcel(merchant, upload.id(), row, "Parcel validation failed; no parcel was created. Correct this row before a new upload.");
                } else {
                    unknown(upload.id(), row.rowNumber());
                }
            } catch (RuntimeException exception) {
                // This includes a local commit failure after a successful downstream write.
                // Preserve the durable reservation; never blindly repeat that remote write.
                unknown(upload.id(), row.rowNumber());
            }
        }
        return store.report(merchant, upload.id());
    }

    private void unknown(UUID upload, int row) {
        store.finish(upload, row, "UNKNOWN", new MerchantBulkResponse.Result(row, false, null,
                List.of("Outcome unknown; this reference is reserved. Do not resubmit until the parcel outcome is reconciled.")));
    }
    private String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
