package com.swiftdrop.integration.merchant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Supplier;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class MerchantBulkStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final IdempotencyRequestHasher hasher;

    public MerchantBulkStore(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager manager,
                             IdempotencyRequestHasher hasher) {
        this.jdbc = jdbc; this.mapper = mapper; this.transaction = new TransactionTemplate(manager); this.hasher = hasher;
    }

    public Upload initialize(UUID merchant, String client, String key, String hash, List<MerchantCsv.Row> rows) {
        return initialize(merchant, client, key, hash, () -> rows);
    }

    public Upload initialize(UUID merchant, String client, String key, String hash, Supplier<List<MerchantCsv.Row>> parse) {
        return transaction.execute(status -> {
            lockApproved(merchant);
            List<Upload> existing = jdbc.query("select id, file_hash from merchant_bulk_uploads where merchant_id=? and idempotency_key=?",
                    (rs, n) -> new Upload(rs.getObject("id", UUID.class), false, rs.getString("file_hash"), List.of()), merchant, key);
            if (!existing.isEmpty()) {
                if (!existing.get(0).hash().equals(hash)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was used with a different CSV file");
                return new Upload(existing.get(0).id(), false, hash, parse.get());
            }
            Integer count = jdbc.queryForObject("select count(*) from merchant_bulk_uploads where merchant_id=? and client_id=? and created_at > current_timestamp - interval '1 minute'",
                    Integer.class, merchant, client);
            if (count >= 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Maximum five new uploads per client per minute");
            List<MerchantCsv.Row> rows = parse.get(); // Existing keys are checked before parsing changed input.
            UUID id = UUID.randomUUID();
            jdbc.update("insert into merchant_bulk_uploads(id,merchant_id,idempotency_key,file_hash,client_id) values (?,?,?,?,?)", id, merchant, key, hash, client);
            for (MerchantCsv.Row row : rows) {
                var result = new MerchantBulkResponse.Result(row.rowNumber(), false, null, row.errors().isEmpty()
                        ? List.of("Not completed; replay this upload to inspect its outcome. Do not resubmit with a new key.") : row.errors());
                jdbc.update("insert into merchant_bulk_rows(upload_id,row_number,row_key,request_hash,state,result_json,recovery_version) values (?,?,?,?,?,?,1)",
                        id, row.rowNumber(), rowKey(merchant, key, row.rowNumber()), row.request() == null ? null : hasher.hash("CREATE_ORDER", row.request()),
                        row.errors().isEmpty() ? "WAITING" : "INVALID", json(result));
            }
            return new Upload(id, true, hash, rows);
        });
    }

    public String rowKey(UUID merchant, String uploadKey, int row) { return hasher.downstreamKey(merchant, "BULK_ROW", uploadKey + ":" + row); }

    /** Retain the same reservation/key and reconcile only attempts using the durable-quote protocol. */
    public boolean claim(UUID merchant, UUID upload, MerchantCsv.Row row, String rowKey) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            lockApproved(merchant);
            var saved = jdbc.queryForMap("select state, request_hash, recovery_version from merchant_bulk_rows where upload_id=? and row_number=?", upload, row.rowNumber());
            String state = (String) saved.get("state");
            if (List.of("SUCCEEDED", "INVALID", "FAILED").contains(state)) return false;
            if (!hasher.hash("CREATE_ORDER", row.request()).equals(saved.get("request_hash"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stored row validation differs; controlled reconciliation is required");
            }
            if (((Number) saved.get("recovery_version")).intValue() == 0 && !state.equals("WAITING")) return false;
            List<String> reservation = jdbc.queryForList("select row_key from merchant_bulk_references where merchant_id=? and external_reference=?", String.class, merchant, row.request().externalReference());
            if (!reservation.isEmpty() && reservation.getFirst().equals(rowKey)) return true;
            boolean used = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from merchant_orders where merchant_id=? and external_reference=?)",
                    Boolean.class, merchant, row.request().externalReference()));
            int inserted = used ? 0 : jdbc.update("insert into merchant_bulk_references(merchant_id,external_reference,upload_id,row_key) values (?,?,?,?) on conflict do nothing",
                    merchant, row.request().externalReference(), upload, rowKey);
            if (inserted == 0) {
                finish(upload, row.rowNumber(), "FAILED", new MerchantBulkResponse.Result(row.rowNumber(), false, null,
                        List.of("externalReference is already used or reserved by this merchant")));
                return false;
            }
            jdbc.update("update merchant_bulk_rows set state='ATTEMPTING', recovery_version=1, result_json=? where upload_id=? and row_number=?",
                    json(new MerchantBulkResponse.Result(row.rowNumber(), false, null, List.of("Outcome unknown or processing; do not resubmit this reference."))), upload, row.rowNumber());
            return true;
        }));
    }

    /** Quote commits separately, before the order transaction or any remote parcel write. */
    public void prepareQuote(UUID merchant, UUID upload, MerchantCsv.Row row, Supplier<BigDecimal> quote) {
        transaction.executeWithoutResult(status -> {
            lockApproved(merchant);
            var saved = jdbc.queryForMap("select r.quoted_fee,r.row_key,r.state from merchant_bulk_rows r where r.upload_id=? and r.row_number=?", upload, row.rowNumber());
            if (saved.get("quoted_fee") != null) return;
            Boolean owned = jdbc.queryForObject("select exists(select 1 from merchant_bulk_references where merchant_id=? and external_reference=? and upload_id=? and row_key=?)",
                    Boolean.class, merchant, row.request().externalReference(), upload, saved.get("row_key"));
            if (!Boolean.TRUE.equals(owned)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Row reservation must be claimed before pricing");
            BigDecimal fee = quote.get();
            if (fee == null || fee.signum() < 0) throw new MerchantOrderService.PricingUnavailableException();
            jdbc.update("update merchant_bulk_rows set quoted_fee=? where upload_id=? and row_number=?", fee, upload, row.rowNumber());
        });
    }

    public BigDecimal requireQuote(UUID merchant, String rowKey) {
        List<BigDecimal> fees = jdbc.queryForList("select r.quoted_fee from merchant_bulk_rows r join merchant_bulk_uploads u on u.id=r.upload_id where u.merchant_id=? and r.row_key=?", BigDecimal.class, merchant, rowKey);
        if (fees.size() != 1 || fees.getFirst() == null) throw new IllegalStateException("Original bulk quote is not available for safe replay");
        return fees.getFirst();
    }

    public void pricingFailed(UUID merchant, UUID upload, MerchantCsv.Row row) {
        transaction.executeWithoutResult(status -> {
            lockApproved(merchant);
            // A concurrent replay may already have pinned a quote; do not clear its reservation.
            BigDecimal fee = jdbc.queryForObject("select quoted_fee from merchant_bulk_rows where upload_id=? and row_number=?", BigDecimal.class, upload, row.rowNumber());
            if (fee != null) return;
            jdbc.update("delete from merchant_bulk_references where merchant_id=? and external_reference=? and upload_id=?", merchant, row.request().externalReference(), upload);
            finish(upload, row.rowNumber(), "RETRYABLE", new MerchantBulkResponse.Result(row.rowNumber(), false, null,
                    List.of("Pricing unavailable; no parcel was requested. Retry this upload with the same file and reference after pricing recovers.")));
        });
    }

    public void assertReferenceAvailable(UUID merchant, String reference, String key, boolean bulk) {
        List<String> reservations = jdbc.queryForList("select row_key from merchant_bulk_references where merchant_id=? and external_reference=?", String.class, merchant, reference);
        if (!reservations.isEmpty() && (!bulk || !reservations.get(0).equals(key))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "externalReference is reserved by a bulk upload");
        }
    }

    public void finish(UUID upload, int row, String state, MerchantBulkResponse.Result result) {
        jdbc.update("update merchant_bulk_rows set state=?, result_json=? where upload_id=? and row_number=? and (state <> 'SUCCEEDED' or ? = 'SUCCEEDED')",
                state, json(result), upload, row, state);
    }

    public void failBeforeParcel(UUID merchant, UUID upload, MerchantCsv.Row row, String message) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("delete from merchant_bulk_references where merchant_id=? and external_reference=? and upload_id=?",
                    merchant, row.request().externalReference(), upload);
            finish(upload, row.rowNumber(), "FAILED", new MerchantBulkResponse.Result(row.rowNumber(), false, null, List.of(message)));
        });
    }

    public MerchantBulkResponse report(UUID merchant, UUID upload) {
        // The local order commit may have succeeded before recording the bulk result.
        // Recover that exact stored response without making another downstream call.
        List<MerchantBulkResponse.Result> recovered = new ArrayList<>();
        List<MerchantBulkResponse.Result> results = jdbc.query("""
                select r.row_number, r.result_json, r.state, k.response_body::text as original
                from merchant_bulk_rows r join merchant_bulk_uploads u on u.id=r.upload_id
                left join idempotency_keys k on k.merchant_id=u.merchant_id and k.operation='CREATE_ORDER'
                    and k.idempotency_key=r.row_key and k.request_hash=r.request_hash and k.processing_state='COMPLETED'
                where u.merchant_id=? and u.id=? order by r.row_number
                """, (rs, n) -> {
                    if ((rs.getString("state").equals("ATTEMPTING") || rs.getString("state").equals("UNKNOWN"))
                            && rs.getString("original") != null) {
                        var result = new MerchantBulkResponse.Result(rs.getInt("row_number"), true,
                                read(rs.getString("original"), MerchantOrderResponse.class), List.of());
                        recovered.add(result);
                        return result;
                    }
                    return read(rs.getString("result_json"), MerchantBulkResponse.Result.class);
                }, merchant, upload);
        for (var result : recovered) finish(upload, result.rowNumber(), "SUCCEEDED", result);
        return MerchantBulkResponse.from(results);
    }

    private void lockApproved(UUID merchant) {
        List<String> states = jdbc.queryForList("select status from merchants where id=? for update", String.class, merchant);
        if (states.isEmpty() || !states.get(0).equals("APPROVED")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An approved merchant is required");
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not store bulk result", e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not read bulk result", e); }
    }
    public record Upload(UUID id, boolean fresh, String hash, List<MerchantCsv.Row> rows) {}
}
