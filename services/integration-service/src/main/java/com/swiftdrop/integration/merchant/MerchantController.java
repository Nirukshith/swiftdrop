package com.swiftdrop.integration.merchant;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping("/api/v1/merchants/register")
    public ResponseEntity<MerchantRegistrationResponse> register(
            @Valid @RequestBody MerchantRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantService.register(request));
    }

    @GetMapping("/api/v1/admin/merchants")
    public MerchantPageResponse list(@RequestParam(required = false) MerchantStatus status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     Authentication authentication) {
        requireAdmin(authentication);
        return merchantService.list(status, page, size);
    }

    @PostMapping("/api/v1/admin/merchants/{merchantId}/approve")
    public MerchantApprovalResponse approve(@PathVariable UUID merchantId, Authentication authentication) {
        requireAdmin(authentication);
        return merchantService.approve(merchantId);
    }

    @PostMapping("/api/v1/admin/merchants/{merchantId}/reject")
    public MerchantSummary reject(@PathVariable UUID merchantId,
                                  @Valid @RequestBody MerchantRejectionRequest request,
                                  Authentication authentication) {
        requireAdmin(authentication);
        return merchantService.reject(merchantId, request.reason());
    }

    private void requireAdmin(Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN") || authority.equals("ADMIN"));
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role is required");
        }
    }
}
