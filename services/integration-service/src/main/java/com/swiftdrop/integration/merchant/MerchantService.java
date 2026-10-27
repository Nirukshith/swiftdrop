package com.swiftdrop.integration.merchant;

import static com.swiftdrop.integration.merchant.AuthProvisioningClient.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final AuthProvisioningClient authProvisioningClient;

    public MerchantService(MerchantRepository merchantRepository, AuthProvisioningClient authProvisioningClient) {
        this.merchantRepository = merchantRepository;
        this.authProvisioningClient = authProvisioningClient;
    }

    public MerchantRegistrationResponse register(MerchantRegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (merchantRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A merchant with this email already exists");
        }

        Merchant merchant = new Merchant(UUID.randomUUID(), request.businessName().trim(), request.contactName().trim(),
                email, request.phoneNumber().trim(), normalize(request.businessRegistrationNumber()), Instant.now());
        try {
            Merchant saved = merchantRepository.saveAndFlush(merchant);
            return MerchantRegistrationResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A merchant with this email already exists");
        }
    }

    public MerchantPageResponse list(MerchantStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Merchant> results = status == null
                ? merchantRepository.findAll(pageable)
                : merchantRepository.findAllByStatus(status, pageable);
        return MerchantPageResponse.from(results);
    }

    public MerchantApprovalResponse approve(UUID merchantId) {
        Merchant merchant = requirePending(merchantId);
        try {
            if (merchant.getAuthUserId() == null) {
                AuthUserResponse user = authProvisioningClient.createMerchantUser(new AuthCreateUserRequest(
                        merchant.getEmail(), null, merchant.getContactName(), merchant.getPhoneNumber(),
                        "MERCHANT", merchant.getId().toString(), false));
                if (user == null || user.id() == null || user.id().isBlank()) {
                    throw new IllegalStateException("Auth service returned no user identifier");
                }
                merchant.setAuthUserId(user.id());
                merchant = merchantRepository.saveAndFlush(merchant);
            }

            authProvisioningClient.activateUser(merchant.getAuthUserId());
            AuthClientCredentialsResponse credentials = authProvisioningClient.createClient(
                    new AuthCreateClientRequest(merchant.getId().toString(), merchant.getBusinessName(), List.of()));
            if (credentials == null || credentials.clientId() == null || credentials.clientSecret() == null) {
                throw new IllegalStateException("Auth service returned incomplete client credentials");
            }

            merchant.approve(credentials.clientId());
            Merchant saved = merchantRepository.saveAndFlush(merchant);
            // Never log or persist this secret in integration-service; auth-service stores its hash.
            return new MerchantApprovalResponse(MerchantSummary.from(saved), credentials.clientId(), credentials.clientSecret());
        } catch (RestClientException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Auth service could not provision merchant credentials", exception);
        }
    }

    public MerchantSummary reject(UUID merchantId, String reason) {
        Merchant merchant = requirePending(merchantId);
        merchant.reject(reason.trim());
        return MerchantSummary.from(merchantRepository.saveAndFlush(merchant));
    }

    private Merchant requirePending(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
        if (!merchant.getStatus().canTransitionTo(MerchantStatus.APPROVED)
                && !merchant.getStatus().canTransitionTo(MerchantStatus.REJECTED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Merchant is not pending");
        }
        return merchant;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
