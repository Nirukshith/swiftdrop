package com.swiftdrop.auth.local;

import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Creates documented local-only sample staff accounts without changing existing users.
 */
@Component
@Profile("local")
public class LocalSampleUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String password;
    private final String courierEmail;
    private final String dispatcherEmail;
    private final String adminEmail;
    private final String customerEmail;
    private final String merchantEmail;

    public LocalSampleUserInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${swiftdrop.auth.sample-users.enabled:true}") boolean enabled,
            @Value("${swiftdrop.auth.sample-users.password:Password123!}") String password,
            @Value("${swiftdrop.auth.sample-users.courier-email:courier@swiftdrop.lk}") String courierEmail,
            @Value("${swiftdrop.auth.sample-users.dispatcher-email:dispatcher@swiftdrop.lk}") String dispatcherEmail,
            @Value("${swiftdrop.auth.sample-users.admin-email:admin@swiftdrop.lk}") String adminEmail,
            @Value("${swiftdrop.auth.sample-users.customer-email:customer@swiftdrop.lk}") String customerEmail,
            @Value("${swiftdrop.auth.sample-users.merchant-email:merchant@swiftdrop.lk}") String merchantEmail) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.password = password;
        this.courierEmail = courierEmail.trim().toLowerCase();
        this.dispatcherEmail = dispatcherEmail.trim().toLowerCase();
        this.adminEmail = adminEmail.trim().toLowerCase();
        this.customerEmail = customerEmail.trim().toLowerCase();
        this.merchantEmail = merchantEmail.trim().toLowerCase();
    }

    @Override
    public void run(String... args) {
        if (!enabled || password.isBlank()) {
            return;
        }

        createIfMissing(courierEmail, "Sample Courier", "COURIER");
        createIfMissing(dispatcherEmail, "Sample Dispatcher", "DISPATCHER");
        createIfMissing(adminEmail, "Sample Administrator", "ADMIN");
        createIfMissing(customerEmail, "Sample Customer", "CUSTOMER");
        createIfMissing(merchantEmail, "Sample Merchant", "MERCHANT");
    }

    private void createIfMissing(String email, String fullName, String role) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        userRepository.save(new User(
                "usr-local-" + role.toLowerCase() + "-" + UUID.randomUUID().toString().replace("-", ""),
                email,
                passwordEncoder.encode(password),
                fullName,
                null,
                role,
                true,
                null
        ));
    }
}
