package com.swiftdrop.auth.internal;

import com.swiftdrop.auth.client.ClientCredential;
import com.swiftdrop.auth.client.ClientCredentialRepository;
import com.swiftdrop.auth.internal.dto.ClientTokenRequest;
import com.swiftdrop.auth.internal.dto.ClientTokenResponse;
import com.swiftdrop.auth.internal.dto.InternalClientCredentialsResponse;
import com.swiftdrop.auth.internal.dto.InternalCreateClientRequest;
import com.swiftdrop.auth.security.jwt.JwtIssuer;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service-to-service internal endpoints for OAuth 2.0 client credential management and token issuance.
 * Blocked at the Nginx Gateway; accessible strictly within the internal container network.
 * Aligned with contracts/openapi/auth.yaml and docs/auth-design.md.
 */
@RestController
@RequestMapping("/internal/v1/clients")
public class InternalClientController {

    private static final List<String> DEFAULT_SCOPES = List.of("parcels:create", "parcels:read", "webhooks:manage");

    private final ClientCredentialRepository clientCredentialRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public InternalClientController(ClientCredentialRepository clientCredentialRepository,
                                    UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    JwtIssuer jwtIssuer) {
        this.clientCredentialRepository = clientCredentialRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    /**
     * Creates an OAuth 2.0 client credential pair (clientId, clientSecret) for an approved merchant.
     */
    @PostMapping
    public ResponseEntity<InternalClientCredentialsResponse> createClientCredentials(
            @Valid @RequestBody InternalCreateClientRequest request) {

        String merchantId = request.merchantId().trim();

        // Check if the associated merchant exists
        boolean merchantExists = userRepository.existsByMerchantId(merchantId);
        if (!merchantExists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Associated merchant not found: " + merchantId);
        }

        String clientId = "sd_live_client_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String clientSecret = "sd_sec_" + UUID.randomUUID().toString().replace("-", "");
        String secretHash = passwordEncoder.encode(clientSecret);

        List<String> scopes = (request.scopes() != null && !request.scopes().isEmpty())
                ? request.scopes()
                : DEFAULT_SCOPES;

        ClientCredential client = new ClientCredential(
                clientId,
                secretHash,
                merchantId,
                request.clientName().trim(),
                String.join(",", scopes)
        );

        ClientCredential saved = clientCredentialRepository.save(client);

        return ResponseEntity.status(HttpStatus.CREATED).body(new InternalClientCredentialsResponse(
                saved.getClientId(),
                clientSecret,
                saved.getMerchantId(),
                saved.getClientName(),
                saved.getScopesList(),
                saved.getCreatedAt()
        ));
    }

    /**
     * Exchanges OAuth2 client credentials for a signed 1-hour RS256 machine-to-machine JWT token.
     */
    @PostMapping("/token")
    public ResponseEntity<ClientTokenResponse> issueClientToken(@Valid @RequestBody ClientTokenRequest request) {
        ClientCredential client = clientCredentialRepository.findByClientId(request.clientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid client credentials"));

        if (!passwordEncoder.matches(request.clientSecret(), client.getClientSecretHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid client credentials");
        }

        // Check if the associated merchant user is enabled
        Optional<User> merchantUser = userRepository.findFirstByMerchantId(client.getMerchantId());
        if (merchantUser.isPresent() && !merchantUser.get().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant account is disabled or pending approval");
        }

        String token = jwtIssuer.issueClientCredentialsToken(
                client.getClientId(),
                client.getMerchantId(),
                client.getScopesList()
        );

        return ResponseEntity.ok(new ClientTokenResponse(
                token,
                "Bearer",
                JwtIssuer.CLIENT_CREDENTIALS_TTL.toSeconds()
        ));
    }
}
