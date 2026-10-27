package com.swiftdrop.auth.internal;

import com.swiftdrop.auth.internal.dto.InternalCreateUserRequest;
import com.swiftdrop.auth.internal.dto.InternalUserResponse;
import com.swiftdrop.auth.internal.dto.UserContactResponse;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import com.swiftdrop.common.Roles;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

/**
 * Service-to-service internal endpoints for user management.
 * Blocked at the Nginx Gateway; accessible strictly within the internal container network.
 * Aligned with contracts/openapi/auth.yaml and docs/auth-design.md.
 */
@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {

    private static final Set<String> VALID_ROLES = Set.of(
            Roles.CUSTOMER,
            Roles.MERCHANT,
            Roles.COURIER,
            Roles.DISPATCHER,
            Roles.ADMIN
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public InternalUserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Internal user contact lookup for M3 notification delivery.
     */
    @GetMapping("/{id}/contact")
    public ResponseEntity<UserContactResponse> getUserContact(@PathVariable("id") String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + id));

        return ResponseEntity.ok(new UserContactResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole()
        ));
    }

    /**
     * Internal user provisioning for M6 merchant onboarding.
     */
    @PostMapping
    public ResponseEntity<InternalUserResponse> createUser(@Valid @RequestBody InternalCreateUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with email already exists: " + normalizedEmail);
        }

        if (!VALID_ROLES.contains(request.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role: " + request.role());
        }

        String passwordHash = null;
        if (request.password() != null && !request.password().isBlank()) {
            passwordHash = passwordEncoder.encode(request.password());
        }

        String userId = "usr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        boolean enabled = Boolean.TRUE.equals(request.enabled());

        User user = new User(
                userId,
                normalizedEmail,
                passwordHash,
                request.fullName().trim(),
                request.phoneNumber() != null ? request.phoneNumber().trim() : null,
                request.role(),
                enabled,
                request.merchantId() != null ? request.merchantId().trim() : null
        );

        User saved = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /**
     * Internal user activation for M6 merchant approval.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<InternalUserResponse> activateUser(@PathVariable("id") String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + id));

        user.setEnabled(true);
        User saved = userRepository.save(user);

        return ResponseEntity.ok(toResponse(saved));
    }

    private InternalUserResponse toResponse(User user) {
        return new InternalUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getMerchantId(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
