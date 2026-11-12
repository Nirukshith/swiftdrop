package com.swiftdrop.auth.internal;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Keeps legacy internal writes from bypassing an established recovery operation. */
@Component
public class MerchantProvisioningGuard {
    private final JdbcTemplate jdbc;
    public MerchantProvisioningGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void checkLegacyWrite(String merchant) {
        if (merchant == null || merchant.isBlank()) return;
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", merchant.trim());
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from merchant_provisioning where merchant_id=?)", Boolean.class, merchant.trim()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Merchant is managed by a provisioning operation; use its explicit recovery API");
        }
    }
}
