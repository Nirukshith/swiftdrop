package com.swiftdrop.auth.admin;

import com.swiftdrop.auth.admin.dto.*;
import com.swiftdrop.auth.event.AuthEventPublisher;
import com.swiftdrop.auth.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthEventPublisher authEventPublisher;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    @DisplayName("listUsers filters by role and enabled status with pagination")
    void listUsers_success() {
        User user = new User("u-1", "staff@swiftdrop.lk", "hash", "Staff One", "+94771234567", "COURIER", true, null);
        PageImpl<User> page = new PageImpl<>(List.of(user));

        when(adminUserRepository.findUsers(eq("COURIER"), eq(true), any(Pageable.class)))
            .thenReturn(page);

        UserListPage result = adminUserService.listUsers("COURIER", true, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).email()).isEqualTo("staff@swiftdrop.lk");
        assertThat(result.content().get(0).role()).isEqualTo("COURIER");
    }

    @Test
    @DisplayName("createStaffUser creates courier, hashes password, saves and publishes event")
    void createStaffUser_courier_success() {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "courier.kamal@swiftdrop.lk",
            "SecurePass#2026",
            "Kamal Gunaratne",
            "+94771234567",
            "COURIER"
        );

        when(adminUserRepository.existsByEmailIgnoreCase("courier.kamal@swiftdrop.lk")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass#2026")).thenReturn("$2a$10$hashedPassword");
        when(adminUserRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSummary summary = adminUserService.createStaffUser(request);

        assertThat(summary.email()).isEqualTo("courier.kamal@swiftdrop.lk");
        assertThat(summary.role()).isEqualTo("COURIER");
        assertThat(summary.fullName()).isEqualTo("Kamal Gunaratne");
        assertThat(summary.enabled()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(adminUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashedPassword");

        verify(authEventPublisher).publishUserRegistered(
            eq(summary.id()),
            eq("courier.kamal@swiftdrop.lk"),
            eq("Kamal Gunaratne"),
            eq("+94771234567"),
            eq("COURIER"),
            isNull(),
            eq(true)
        );
    }

    @Test
    @DisplayName("createStaffUser creates dispatcher account successfully")
    void createStaffUser_dispatcher_success() {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "dispatch.saman@swiftdrop.lk",
            "SecurePass#2026",
            "Saman Perera",
            "+94777654321",
            "DISPATCHER"
        );

        when(adminUserRepository.existsByEmailIgnoreCase("dispatch.saman@swiftdrop.lk")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass#2026")).thenReturn("$2a$10$hashedPassword");
        when(adminUserRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSummary summary = adminUserService.createStaffUser(request);

        assertThat(summary.role()).isEqualTo("DISPATCHER");
        verify(authEventPublisher).publishUserRegistered(
            anyString(),
            eq("dispatch.saman@swiftdrop.lk"),
            eq("Saman Perera"),
            eq("+94777654321"),
            eq("DISPATCHER"),
            isNull(),
            eq(true)
        );
    }

    @Test
    @DisplayName("createStaffUser rejects invalid staff role (e.g. CUSTOMER, ADMIN)")
    void createStaffUser_rejectsInvalidRole() {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "customer@swiftdrop.lk",
            "SecurePass#2026",
            "Customer User",
            "+94771234567",
            "CUSTOMER"
        );

        assertThatThrownBy(() -> adminUserService.createStaffUser(request))
            .isInstanceOf(InvalidStaffRoleException.class)
            .hasMessageContaining("COURIER or DISPATCHER");

        verifyNoInteractions(adminUserRepository);
        verifyNoInteractions(authEventPublisher);
    }

    @Test
    @DisplayName("createStaffUser rejects duplicate email address")
    void createStaffUser_rejectsDuplicateEmail() {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "existing@swiftdrop.lk",
            "SecurePass#2026",
            "Existing User",
            "+94771234567",
            "COURIER"
        );

        when(adminUserRepository.existsByEmailIgnoreCase("existing@swiftdrop.lk")).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.createStaffUser(request))
            .isInstanceOf(EmailAlreadyExistsException.class)
            .hasMessageContaining("already exists");

        verify(adminUserRepository, never()).save(any());
        verifyNoInteractions(authEventPublisher);
    }

    @Test
    @DisplayName("updateUserStatus updates enabled flag")
    void updateUserStatus_success() {
        User user = new User("u-1", "user@swiftdrop.lk", "hash", "User One", "+94771234567", "COURIER", true, null);

        when(adminUserRepository.findById("u-1")).thenReturn(Optional.of(user));
        when(adminUserRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSummary summary = adminUserService.updateUserStatus("u-1", false);

        assertThat(summary.enabled()).isFalse();
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("updateUserStatus throws UserNotFoundException when user does not exist")
    void updateUserStatus_notFound() {
        when(adminUserRepository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.updateUserStatus("unknown-id", false))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining("not found");
    }
}
