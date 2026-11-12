package com.swiftdrop.auth.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.internal.dto.InternalUserResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MerchantProvisioningService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public MerchantProvisioningService(JdbcTemplate jdbc, PasswordEncoder passwords, ObjectMapper mapper) {
        this.jdbc = jdbc; this.passwords = passwords; this.mapper = mapper;
    }

    @Transactional
    public Result provision(UUID operation, MerchantProvisioningController.ProvisionRequest request) {
        String merchant = request.merchantId().toString();
        lock(merchant);
        String fingerprint = fingerprint(request);
        var existing = jdbc.queryForList("select operation_id, merchant_id, request_hash from merchant_provisioning where operation_id=? or merchant_id=?",
                operation, merchant);
        if (!existing.isEmpty()) {
            if (existing.size() != 1 || !operation.equals(existing.getFirst().get("operation_id"))
                    || !merchant.equals(existing.getFirst().get("merchant_id"))
                    || !fingerprint.equals(existing.getFirst().get("request_hash"))) {
                throw conflict("Provisioning operation or merchant profile conflicts");
            }
            Result result = status(operation);
            verifyExpectedUser(request.expectedUserId(), result.user().id());
            return result;
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        var users = jdbc.queryForList("select id, email, role from users where merchant_id=?", merchant);
        String user;
        if (users.isEmpty()) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from users where lower(email)=?)", Boolean.class, email))) {
                throw conflict("Email belongs to an existing account without this merchant association");
            }
            if (request.expectedUserId() != null) throw conflict("Expected merchant user was not found");
            user = "usr-" + UUID.randomUUID();
            jdbc.update("insert into users(id,email,full_name,phone_number,role,enabled,merchant_id,created_at,updated_at) values (?,?,?,?,'MERCHANT',true,?,current_timestamp,current_timestamp)",
                    user, email, request.fullName().trim(), request.phoneNumber().trim(), merchant);
        } else {
            if (users.size() != 1 || !email.equals(users.getFirst().get("email")) || !"MERCHANT".equals(users.getFirst().get("role"))) {
                throw conflict("Merchant account association is ambiguous or does not match");
            }
            user = (String) users.getFirst().get("id");
            verifyExpectedUser(request.expectedUserId(), user);
        }
        var clients = jdbc.queryForList("select client_id, status from clients where merchant_id=?", merchant);
        String client;
        String secret = null;
        if (clients.isEmpty()) {
            client = "sd_live_client_" + UUID.randomUUID().toString().replace("-", "");
            secret = newSecret();
            jdbc.update("insert into clients(client_id,client_secret_hash,merchant_id,client_name,scopes,status,created_at,updated_at) values (?,?,?,?,'parcels:create,parcels:read,webhooks:manage','ACTIVE',current_timestamp,current_timestamp)",
                    client, passwords.encode(secret), merchant, request.fullName().trim());
        } else {
            if (clients.size() != 1 || !"ACTIVE".equals(clients.getFirst().get("status"))) {
                throw conflict("Merchant client association is ambiguous or inactive");
            }
            client = (String) clients.getFirst().get("client_id");
        }
        jdbc.update("update users set enabled=true, updated_at=current_timestamp where id=?", user);
        jdbc.update("insert into merchant_provisioning(operation_id,merchant_id,request_hash,user_id,client_id,actor_id) values (?,?,?,?,?,?)",
                operation, merchant, fingerprint, user, client, request.actorId());
        event(operation, null, request.actorId(), "PROVISIONED");
        return result(operation, secret);
    }

    @Transactional(readOnly = true)
    public Result status(UUID operation) { return result(operation, null); }

    @Transactional
    public Result replace(UUID operation, MerchantProvisioningController.ReplacementRequest request) {
        if (!Boolean.TRUE.equals(request.confirmReplacement())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit credential replacement confirmation is required");
        Result initial = result(operation, null);
        lock(initial.merchantId().toString());
        lock("replacement:" + request.replacementId());
        Result current = result(operation, null);
        var previous = jdbc.queryForList("select operation_id from merchant_credential_replacements where replacement_id=?", request.replacementId());
        if (!previous.isEmpty()) {
            if (!operation.equals(previous.getFirst().get("operation_id"))) throw conflict("Replacement identity belongs to another operation");
            return current;
        }
        if (!current.user().enabled() || !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from clients where client_id=? and merchant_id=? and status='ACTIVE')",
                Boolean.class, current.clientId(), current.merchantId().toString()))) {
            throw conflict("Merchant account or client is inactive");
        }
        String secret = newSecret();
        jdbc.update("update clients set client_secret_hash=?,updated_at=current_timestamp where client_id=?", passwords.encode(secret), current.clientId());
        jdbc.update("insert into merchant_credential_replacements(replacement_id,operation_id,actor_id) values (?,?,?)",
                request.replacementId(), operation, request.actorId());
        event(operation, request.replacementId(), request.actorId(), "CREDENTIAL_REPLACED");
        return result(operation, secret);
    }

    private Result result(UUID operation, String secret) {
        List<Result> rows = jdbc.query("""
                select p.merchant_id, p.client_id, u.id, u.email, u.full_name, u.phone_number,
                       u.role, u.enabled, u.merchant_id as user_merchant_id, u.created_at
                from merchant_provisioning p join users u on u.id=p.user_id
                join clients c on c.client_id=p.client_id and c.merchant_id=p.merchant_id
                where p.operation_id=?
                """, (rs, n) -> {
            if (!"MERCHANT".equals(rs.getString("role")) || !rs.getString("merchant_id").equals(rs.getString("user_merchant_id"))) {
                throw conflict("Provisioned user association no longer matches");
            }
            return new Result(operation, UUID.fromString(rs.getString("merchant_id")),
                    new InternalUserResponse(rs.getString("id"), rs.getString("email"), rs.getString("full_name"),
                            rs.getString("phone_number"), rs.getString("role"), rs.getString("merchant_id"),
                            rs.getBoolean("enabled"), rs.getTimestamp("created_at").toInstant()),
                    rs.getString("client_id"), secret, secret == null);
        }, operation);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provisioning operation not found");
        return rows.getFirst();
    }

    private void lock(String merchant) { jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", merchant); }
    private void verifyExpectedUser(String expected, String actual) {
        if (expected != null && !expected.equals(actual)) throw conflict("Expected user does not match the merchant association");
    }
    private String newSecret() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return "sd_sec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private void event(UUID operation, UUID replacement, String actor, String action) {
        jdbc.update("insert into merchant_provisioning_events(operation_id,replacement_id,actor_id,action) values (?,?,?,?)", operation, replacement, actor, action);
    }
    private String fingerprint(MerchantProvisioningController.ProvisionRequest request) {
        try {
            String json = mapper.writeValueAsString(List.of(request.merchantId().toString(), request.email().trim().toLowerCase(Locale.ROOT), request.fullName().trim(), request.phoneNumber().trim()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) { throw new IllegalStateException("Could not fingerprint provisioning request"); }
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    public record Result(UUID operationId, UUID merchantId, InternalUserResponse user, String clientId,
                         String clientSecret, boolean credentialRecoveryRequired) {
        @Override public String toString() { return "ProvisioningResult[operationId=" + operationId + ", credentialRecoveryRequired=" + credentialRecoveryRequired + "]"; }
    }
}
