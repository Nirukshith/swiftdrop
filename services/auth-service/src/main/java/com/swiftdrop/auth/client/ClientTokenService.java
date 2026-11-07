package com.swiftdrop.auth.client;

import com.swiftdrop.auth.internal.dto.ClientTokenRequest;
import com.swiftdrop.auth.internal.dto.ClientTokenResponse;
import com.swiftdrop.auth.security.jwt.JwtIssuer;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class ClientTokenService {

    private final ClientCredentialRepository clientCredentialRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public ClientTokenService(ClientCredentialRepository clientCredentialRepository,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              JwtIssuer jwtIssuer) {
        this.clientCredentialRepository = clientCredentialRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    public ClientTokenResponse exchangeToken(ClientTokenRequest request, String authorizationHeader) {
        String clientId = request != null ? request.clientId() : null;
        String clientSecret = request != null ? request.clientSecret() : null;
        String grantType = request != null ? request.grantType() : null;
        String scope = request != null ? request.scope() : null;

        // Support HTTP Basic Auth if credentials are not in request body
        if ((clientId == null || clientId.isBlank()) && authorizationHeader != null && authorizationHeader.startsWith("Basic ")) {
            String base64Credentials = authorizationHeader.substring("Basic ".length()).trim();
            try {
                byte[] decoded = Base64.getDecoder().decode(base64Credentials);
                String credentials = new String(decoded, StandardCharsets.UTF_8);
                String[] values = credentials.split(":", 2);
                if (values.length == 2) {
                    clientId = values[0];
                    clientSecret = values[1];
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore decoding error; will fail on blank check below
            }
        }

        if (grantType != null && !grantType.isBlank() && !"client_credentials".equalsIgnoreCase(grantType.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported grant_type: " + grantType);
        }

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing client credentials");
        }

        ClientCredential client = clientCredentialRepository.findByClientId(clientId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid client credentials"));

        if (!passwordEncoder.matches(clientSecret, client.getClientSecretHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid client credentials");
        }

        if (!client.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client is disabled or revoked");
        }

        Optional<User> merchantUser = userRepository.findFirstByMerchantId(client.getMerchantId());
        if (merchantUser.isEmpty() || !merchantUser.get().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant account is disabled or pending approval");
        }

        List<String> assignedScopes = client.getScopesList();
        List<String> effectiveScopes;

        if (scope != null && !scope.isBlank()) {
            List<String> requestedScopes = Arrays.stream(scope.split("[ ,]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            for (String s : requestedScopes) {
                if (!assignedScopes.contains(s)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested scope not authorized: " + s);
                }
            }
            effectiveScopes = requestedScopes;
        } else {
            effectiveScopes = assignedScopes;
        }

        String token = jwtIssuer.issueClientCredentialsToken(
                client.getClientId(),
                client.getMerchantId(),
                effectiveScopes
        );

        return new ClientTokenResponse(
                token,
                "Bearer",
                JwtIssuer.CLIENT_CREDENTIALS_TTL.toSeconds(),
                String.join(" ", effectiveScopes)
        );
    }
}
