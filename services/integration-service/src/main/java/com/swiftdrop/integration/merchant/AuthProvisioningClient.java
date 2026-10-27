package com.swiftdrop.integration.merchant;

import java.util.List;

public interface AuthProvisioningClient {
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
}
