package com.swiftdrop.integration.merchant;

import java.util.List;
import java.util.UUID;

public interface AuthProvisioningClient {
    ProvisioningResponse provision(UUID operationId, ProvisioningRequest request);
    ProvisioningResponse provisioningStatus(UUID operationId);
    ProvisioningResponse replaceCredential(UUID operationId, ReplacementRequest request);
    AuthUserResponse createMerchantUser(AuthCreateUserRequest request);
    AuthUserResponse activateUser(String userId);
    AuthClientCredentialsResponse createClient(AuthCreateClientRequest request);

    record AuthCreateUserRequest(String email, String password, String fullName, String phoneNumber,
                                 String role, String merchantId, Boolean enabled) {}
    record AuthUserResponse(String id, String email, String fullName, String phoneNumber,
                            String role, String merchantId, boolean enabled) {}
    record AuthCreateClientRequest(String merchantId, String clientName, List<String> scopes) {}
    record AuthClientCredentialsResponse(String clientId, String clientSecret, String merchantId,
                                         String clientName, List<String> scopes) {}

    record ProvisioningRequest(UUID merchantId, String email, String fullName, String phoneNumber,
                               String expectedUserId, String actorId) {}
    record ReplacementRequest(UUID replacementId, String actorId, boolean confirmReplacement) {}
    record ProvisioningResponse(UUID operationId, UUID merchantId, AuthUserResponse user, String clientId,
                                String clientSecret, boolean credentialRecoveryRequired) {
        @Override public String toString() { return "ProvisioningResponse[operationId=" + operationId + ", credentialRecoveryRequired=" + credentialRecoveryRequired + "]"; }
    }
}
