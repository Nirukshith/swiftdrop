package com.swiftdrop.integration.merchant;

import static com.swiftdrop.integration.merchant.AuthProvisioningClient.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MerchantRecoveryService {
    private final MerchantRepository merchants;
    private final AuthProvisioningClient auth;
    private final MerchantApprovalMailSender approvalMailSender;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public MerchantRecoveryService(MerchantRepository merchants, AuthProvisioningClient auth,
            MerchantApprovalMailSender approvalMailSender, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.merchants = merchants; this.auth = auth; this.approvalMailSender = approvalMailSender; this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    public MerchantApprovalResponse approve(UUID merchant) { return run(merchant, null); }

    public MerchantApprovalResponse recover(UUID merchant, UUID replacement) { return run(merchant, replacement); }

    private MerchantApprovalResponse run(UUID id, UUID replacement) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        // Commit the stable operation BEFORE any remote call, including calls that time out.
        UUID operation = transaction.execute(status -> {
            Merchant merchant = lock(id);
            requireState(merchant, replacement != null);
            if (replacement != null && !merchant.isCredentialRecoveryAvailable()) throw conflict("Approve this merchant before requesting credential recovery");
            if (merchant.getProvisioningOperationId() == null) {
                merchant.beginProvisioning(UUID.randomUUID());
                merchants.saveAndFlush(merchant);
                audit(merchant, replacement, actor, "STARTED");
            }
            return merchant.getProvisioningOperationId();
        });
        MerchantApprovalResponse result;
        AtomicReference<String> temporaryPassword = new AtomicReference<>();
        try {
            result = transaction.execute(status -> {
                Merchant merchant = lock(id);
                requireState(merchant, replacement != null);
                var response = auth.provision(operation, new ProvisioningRequest(id, merchant.getEmail(),
                        merchant.getContactName(), merchant.getPhoneNumber(), merchant.getAuthUserId(), actor));
                validate(response, operation, merchant);
                temporaryPassword.set(response.temporaryPassword());
                if (replacement != null) {
                    merchant.recordProvisioned(response.user().id(), response.clientId());
                    response = auth.replaceCredential(operation, new ReplacementRequest(replacement, actor, true));
                    validate(response, operation, merchant);
                }
                merchant.recordProvisioned(response.user().id(), response.clientId());
                if (response.credentialRecoveryRequired()) {
                    merchants.saveAndFlush(merchant);
                    audit(merchant, replacement, actor, "HANDOFF_REQUIRED");
                    return null;
                }
                if (merchant.getStatus() == MerchantStatus.PENDING) merchant.approve(response.clientId());
                Merchant saved = merchants.saveAndFlush(merchant);
                audit(saved, replacement, actor, replacement == null ? "APPROVED" : "CREDENTIAL_REPLACED");
                return new MerchantApprovalResponse(MerchantSummary.from(saved), response.clientId(), response.clientSecret());
            });
        } catch (org.springframework.web.client.HttpStatusCodeException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered to another account and cannot be approved as a merchant.");
            }
            transaction.executeWithoutResult(status -> audit(lock(id), replacement, actor, "OUTCOME_UNAVAILABLE"));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auth provisioning outcome unavailable; retry safely or use ADMIN credential recovery");
        } catch (RestClientException | IllegalStateException | org.springframework.dao.DataAccessException exception) {
            transaction.executeWithoutResult(status -> audit(lock(id), replacement, actor, "OUTCOME_UNAVAILABLE"));
            // The provider may have committed; never expose its body or generate another operation.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auth provisioning outcome unavailable; retry safely or use ADMIN credential recovery");
        }
        if (result == null) {
            throw conflict(replacement == null
                    ? "Credential handoff requires explicit ADMIN recovery; the existing client was not duplicated"
                    : "Replacement already completed and its secret cannot be replayed; confirm another recovery with a new key if the handoff was lost");
        }
        if (replacement == null && result.merchant().status() == MerchantStatus.APPROVED) {
            approvalMailSender.sendApprovalEmail(
                    result.merchant().email(), result.merchant().businessName(), temporaryPassword.get());
        }
        return result;
    }

    public MerchantSummary reject(UUID id, String reason) {
        String actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName()
                : "admin";
        MerchantSummary summary = transaction.execute(status -> {
            Merchant merchant = lock(id);
            requireState(merchant, false);
            if (merchant.getAuthUserId() != null || merchant.getClientId() != null) {
                throw conflict("Provisioning has begun; reconcile the account before rejecting this merchant");
            }
            merchant.reject(reason.trim());
            return MerchantSummary.from(merchants.saveAndFlush(merchant));
        });
        if (summary != null) {
            approvalMailSender.sendRejectionEmail(summary.email(), summary.businessName(), summary.rejectionReason());
            auth.recordRejection(id, new AuthProvisioningClient.RejectionAuditRequest(
                    summary.businessName(), summary.email(), summary.rejectionReason(), actor));
        }
        return summary;
    }

    private void validate(ProvisioningResponse response, UUID operation, Merchant merchant) {
        if (response == null || !operation.equals(response.operationId()) || !merchant.getId().equals(response.merchantId())
                || response.user() == null || response.user().id() == null || response.user().id().isBlank()
                || !"MERCHANT".equals(response.user().role()) || !response.user().enabled()
                || !merchant.getId().toString().equals(response.user().merchantId())
                || response.user().email() == null || !merchant.getEmail().equalsIgnoreCase(response.user().email().trim())
                || merchant.getAuthUserId() != null && !merchant.getAuthUserId().equals(response.user().id())
                || response.clientId() == null || response.clientId().isBlank()
                || merchant.getClientId() != null && !merchant.getClientId().equals(response.clientId())
                || response.credentialRecoveryRequired() != (response.clientSecret() == null)
                || response.clientSecret() != null && response.clientSecret().isBlank()) {
            throw new IllegalStateException("Invalid merchant provisioning response");
        }
    }
    private Merchant lock(UUID id) {
        return merchants.findForOrder(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
    }
    private void requireState(Merchant merchant, boolean recovery) {
        if (merchant.getStatus() != MerchantStatus.PENDING && !(recovery && merchant.getStatus() == MerchantStatus.APPROVED)) {
            throw conflict("Merchant is not in a state eligible for this action");
        }
    }
    private void audit(Merchant merchant, UUID replacement, String actor, String outcome) {
        jdbc.update("insert into merchant_provisioning_audit(merchant_id,operation_id,replacement_id,actor_id,outcome) values (?,?,?,?,?)",
                merchant.getId(), merchant.getProvisioningOperationId(), replacement, actor, outcome);
    }
    private ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
}
