package com.swiftdrop.auth.client;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.swiftdrop.auth.internal.dto.ClientTokenRequest;
import com.swiftdrop.auth.internal.dto.ClientTokenResponse;
import com.swiftdrop.auth.security.jwt.JwtIssuer;
import com.swiftdrop.auth.security.jwt.JwtKeyProvider;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import com.swiftdrop.common.Roles;
import com.swiftdrop.common.security.JwtClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientTokenServiceTest {

    @Mock
    private ClientCredentialRepository clientCredentialRepository;

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private JwtIssuer jwtIssuer;
    private JwtKeyProvider keyProvider;
    private ClientTokenService clientTokenService;

    private static final String CLIENT_ID = "sd_live_client_1234567890ab";
    private static final String RAW_SECRET = "sd_sec_raw_secret_value_1234";
    private static final String MERCHANT_ID = "mch-uuid-112233";

    private ClientCredential activeClient;
    private User activeMerchantUser;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        keyProvider = new JwtKeyProvider("", "", JwtKeyProvider.DEV_KEY_ID);
        jwtIssuer = new JwtIssuer(keyProvider);

        clientTokenService = new ClientTokenService(
                clientCredentialRepository,
                userRepository,
                passwordEncoder,
                jwtIssuer
        );

        activeClient = new ClientCredential(
                CLIENT_ID,
                passwordEncoder.encode(RAW_SECRET),
                MERCHANT_ID,
                "Store API",
                "parcels:create,parcels:read,webhooks:manage",
                "ACTIVE"
        );

        activeMerchantUser = new User(
                "usr-mch-1",
                "merchant@store.com",
                passwordEncoder.encode("Pass123!"),
                "Merchant Store",
                "+94771234567",
                Roles.MERCHANT,
                true, // enabled
                MERCHANT_ID
        );

        when(clientCredentialRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(activeClient));
        when(userRepository.findFirstByMerchantId(MERCHANT_ID)).thenReturn(Optional.of(activeMerchantUser));
    }

    @Test
    @DisplayName("Valid client credentials exchange returns RS256 token valid for 15 minutes (900 seconds)")
    void validClientCredentials_succeeds() throws Exception {
        ClientTokenRequest request = new ClientTokenRequest(CLIENT_ID, RAW_SECRET, "client_credentials", null);

        ClientTokenResponse response = clientTokenService.exchangeToken(request, null);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.scope()).isEqualTo("parcels:create parcels:read webhooks:manage");

        SignedJWT jwt = SignedJWT.parse(response.accessToken());
        JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keyProvider.getPublicKey());
        assertThat(jwt.verify(verifier)).isTrue();

        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("https://auth.swiftdrop.local");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getJWTClaimsSet().getStringClaim(JwtClaims.ROLE)).isEqualTo(Roles.MERCHANT);
        assertThat(jwt.getJWTClaimsSet().getStringListClaim(JwtClaims.ROLES)).containsExactly("ROLE_MERCHANT");
        assertThat(jwt.getJWTClaimsSet().getStringClaim(JwtClaims.MERCHANT_ID)).isEqualTo(MERCHANT_ID);
        assertThat(jwt.getJWTClaimsSet().getStringClaim(JwtClaims.SCOPE)).isEqualTo("parcels:create parcels:read webhooks:manage");
    }

    @Test
    @DisplayName("HTTP Basic Auth header succeeds when body credentials are null")
    void httpBasicAuth_succeeds() {
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + RAW_SECRET).getBytes());

        ClientTokenResponse response = clientTokenService.exchangeToken(null, basicAuth);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("Unknown client ID returns 401 Unauthorized")
    void unknownClientId_throws401() {
        when(clientCredentialRepository.findByClientId("unknown_client")).thenReturn(Optional.empty());
        ClientTokenRequest request = new ClientTokenRequest("unknown_client", RAW_SECRET);

        assertThatThrownBy(() -> clientTokenService.exchangeToken(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Wrong client secret returns 401 Unauthorized")
    void wrongSecret_throws401() {
        ClientTokenRequest request = new ClientTokenRequest(CLIENT_ID, "wrong_secret");

        assertThatThrownBy(() -> clientTokenService.exchangeToken(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Disabled client returns 403 Forbidden")
    void disabledClient_throws403() {
        activeClient.setStatus("DISABLED");
        ClientTokenRequest request = new ClientTokenRequest(CLIENT_ID, RAW_SECRET);

        assertThatThrownBy(() -> clientTokenService.exchangeToken(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Revoked client returns 403 Forbidden")
    void revokedClient_throws403() {
        activeClient.setStatus("REVOKED");
        ClientTokenRequest request = new ClientTokenRequest(CLIENT_ID, RAW_SECRET);

        assertThatThrownBy(() -> clientTokenService.exchangeToken(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Pending/disabled merchant user returns 403 Forbidden")
    void pendingMerchantUser_throws403() {
        activeMerchantUser.setEnabled(false);
        ClientTokenRequest request = new ClientTokenRequest(CLIENT_ID, RAW_SECRET);

        assertThatThrownBy(() -> clientTokenService.exchangeToken(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Unsupported grant_type returns 400 Bad Request")
    void unsupportedGrantType_throws400() {
        ClientTokenRequest request = new ClientTokenRequest(CLIENT_ID, RAW_SECRET, "password", null);

        assertThatThrownBy(() -> clientTokenService.exchangeToken(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Requesting allowed scope subset issues token with only requested scopes")
    void scopeSubset_succeeds() throws Exception {
        ClientTokenRequest request = new ClientTokenRequest(CLIENT_ID, RAW_SECRET, "client_credentials", "parcels:create parcels:read");

        ClientTokenResponse response = clientTokenService.exchangeToken(request, null);

        assertThat(response.scope()).isEqualTo("parcels:create parcels:read");
        SignedJWT jwt = SignedJWT.parse(response.accessToken());
        assertThat(jwt.getJWTClaimsSet().getStringClaim(JwtClaims.SCOPE)).isEqualTo("parcels:create parcels:read");
    }

    @Test
    @DisplayName("Scope escalation (requesting unassigned scope) returns 400 Bad Request")
    void scopeEscalation_throws400() {
        ClientTokenRequest request = new ClientTokenRequest(CLIENT_ID, RAW_SECRET, "client_credentials", "parcels:create admin:all");

        assertThatThrownBy(() -> clientTokenService.exchangeToken(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
