package com.swiftdrop.integration.merchant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Repository
public class MerchantOrderIdempotencyStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MerchantOrderIdempotencyStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** Returns the stored response for a replay, or null after reserving a new key. */
    public StoredResponse reserveOrReplay(UUID merchantId, String operation, String key, String requestHash) {
        jdbcTemplate.update("""
                delete from idempotency_keys
                where merchant_id = ? and operation = ? and idempotency_key = ? and expires_at <= ?
                """, merchantId, operation, key, Timestamp.from(Instant.now()));

        int inserted = jdbcTemplate.update("""
                insert into idempotency_keys
                    (merchant_id, operation, idempotency_key, request_hash, processing_state, created_at, expires_at)
                values (?, ?, ?, ?, 'PROCESSING', ?, ?)
                on conflict (merchant_id, operation, idempotency_key) do nothing
                """, merchantId, operation, key, requestHash,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        if (inserted == 1) {
            return null;
        }

        StoredRow row = jdbcTemplate.queryForObject("""
                select request_hash, processing_state, response_status, response_body::text as response_body
                from idempotency_keys
                where merchant_id = ? and operation = ? and idempotency_key = ?
                for update
                """, (result, rowNum) -> new StoredRow(
                result.getString("request_hash").trim(),
                result.getString("processing_state"),
                (Integer) result.getObject("response_status"),
                result.getString("response_body")), merchantId, operation, key);

        if (!row.requestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different request body");
        }
        if (!"COMPLETED".equals(row.state()) || row.responseBody() == null || row.responseStatus() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A request with this Idempotency-Key is still processing");
        }
        try {
            return new StoredResponse(row.responseStatus(), objectMapper.readValue(row.responseBody(), MerchantOrderResponse.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored idempotency response could not be read", exception);
        }
    }

    public void complete(UUID merchantId, String operation, String key, int status, MerchantOrderResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            int updated = jdbcTemplate.update("""
                    update idempotency_keys
                    set processing_state = 'COMPLETED', response_status = ?, response_body = cast(? as jsonb)
                    where merchant_id = ? and operation = ? and idempotency_key = ?
                    """, status, json, merchantId, operation, key);
            if (updated != 1) {
                throw new IllegalStateException("Idempotency reservation was lost before completion");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store idempotency response", exception);
        }
    }

    private record StoredRow(String requestHash, String state, Integer responseStatus, String responseBody) {}
    public record StoredResponse(int status, MerchantOrderResponse body) {}
}
