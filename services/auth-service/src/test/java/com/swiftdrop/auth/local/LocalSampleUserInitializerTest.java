package com.swiftdrop.auth.local;

import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalSampleUserInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsEnabledSampleStaffWhenSampleEmailsAreMissing() {
        when(userRepository.existsByEmailIgnoreCase("courier@swiftdrop.lk")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");

        new LocalSampleUserInitializer(
                userRepository,
                passwordEncoder,
                true,
                "Password123!",
                "courier@swiftdrop.lk",
                "dispatcher@swiftdrop.lk",
                "admin@swiftdrop.lk"
        ).run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertEquals(3, captor.getAllValues().size());
        assertEquals(
                java.util.Set.of("COURIER", "DISPATCHER", "ADMIN"),
                captor.getAllValues().stream().map(User::getRole).collect(java.util.stream.Collectors.toSet())
        );
        org.junit.jupiter.api.Assertions.assertTrue(captor.getAllValues().stream().allMatch(User::isEnabled));
    }

    @Test
    void doesNotOverwriteExistingAccount() {
        when(userRepository.existsByEmailIgnoreCase("courier@swiftdrop.lk")).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("dispatcher@swiftdrop.lk")).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("admin@swiftdrop.lk")).thenReturn(true);

        new LocalSampleUserInitializer(
                userRepository,
                passwordEncoder,
                true,
                "Password123!",
                "courier@swiftdrop.lk",
                "dispatcher@swiftdrop.lk",
                "admin@swiftdrop.lk"
        ).run();

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }
}
