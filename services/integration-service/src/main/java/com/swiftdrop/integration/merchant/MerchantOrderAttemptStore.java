package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Durable single-order intent, committed before contacting the parcel provider. */
@Repository
public class MerchantOrderAttemptStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public MerchantOrderAttemptStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    public void prepare(UUID merchant, String key, String hash, String reference, Supplier<BigDecimal> quote) {
        transaction.executeWithoutResult(status -> {
            List<String> states = jdbc.queryForList("select status from merchants where id=? for update", String.class, merchant);
            if (states.isEmpty() || !"APPROVED".equals(states.getFirst())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An approved merchant is required");
            }
            // Completed response replay must not request another quote or reserve another reference.
            Boolean completed = jdbc.queryForObject("""
                    select exists(select 1 from idempotency_keys where merchant_id=? and operation='CREATE_ORDER'
                        and idempotency_key=? and processing_state='COMPLETED' and expires_at > current_timestamp)
                    """, Boolean.class, merchant, key);
            if (Boolean.TRUE.equals(completed)) return;
            List<String> hashes = jdbc.queryForList("select request_hash from merchant_order_attempts where merchant_id=? and idempotency_key=?",
                    String.class, merchant, key);
            if (!hashes.isEmpty()) {
                if (!hashes.getFirst().equals(hash)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used with a different request body");
                }
                return;
            }
            Boolean used = jdbc.queryForObject("""
                    select exists(select 1 from merchant_orders where merchant_id=? and external_reference=?)
                        or exists(select 1 from merchant_order_attempts where merchant_id=? and external_reference=?)
                        or exists(select 1 from merchant_bulk_references where merchant_id=? and external_reference=?)
                    """, Boolean.class, merchant, reference, merchant, reference, merchant, reference);
            if (Boolean.TRUE.equals(used)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "externalReference is already used or reserved by this merchant");
            }
            BigDecimal fee = quote.get();
            if (fee == null || fee.signum() < 0) throw new MerchantOrderService.PricingUnavailableException();
            jdbc.update("""
                    insert into merchant_order_attempts(merchant_id,idempotency_key,external_reference,request_hash,quoted_fee)
                    values (?,?,?,?,?)
                    """, merchant, key, reference, hash, fee);
        });
    }

    public BigDecimal requireQuote(UUID merchant, String key) {
        return jdbc.queryForObject("select quoted_fee from merchant_order_attempts where merchant_id=? and idempotency_key=?",
                BigDecimal.class, merchant, key);
    }

    /** Called in the same transaction that stores the order and its complete response. */
    public void complete(UUID merchant, String key) {
        jdbc.update("delete from merchant_order_attempts where merchant_id=? and idempotency_key=?", merchant, key);
    }

    /** Only explicit provider validation failures establish that no parcel was written. */
    public void validationFailed(UUID merchant, String key) {
        transaction.executeWithoutResult(status -> {
            jdbc.queryForList("select id from merchants where id=? for update", UUID.class, merchant);
            complete(merchant, key);
        });
    }
}
