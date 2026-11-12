package com.swiftdrop.integration.merchant;


import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantRecoveryService recovery;

    public MerchantService(MerchantRepository merchantRepository, MerchantRecoveryService recovery) {
        this.merchantRepository = merchantRepository;
        this.recovery = recovery;
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
        return recovery.approve(merchantId);
    }

    public MerchantSummary reject(UUID merchantId, String reason) {
        return recovery.reject(merchantId, reason);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
