package com.swiftdrop.auth.admin;

import com.swiftdrop.auth.admin.dto.*;
import com.swiftdrop.auth.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AdminUserIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        adminUserRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-end: Admin provisions courier staff account in database")
    void createCourierStaffAccount_success() {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "courier.sunil@swiftdrop.lk",
            "SecurePass#2026",
            "Sunil Perera",
            "+94771234567",
            "COURIER"
        );

        UserSummary summary = adminUserService.createStaffUser(request);

        assertThat(summary.id()).isNotNull();
        assertThat(summary.email()).isEqualTo("courier.sunil@swiftdrop.lk");
        assertThat(summary.role()).isEqualTo("COURIER");
        assertThat(summary.enabled()).isTrue();

        Optional<User> persisted = adminUserRepository.findById(summary.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getEmail()).isEqualTo("courier.sunil@swiftdrop.lk");
        assertThat(passwordEncoder.matches("SecurePass#2026", persisted.get().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("End-to-end: Admin provisions dispatcher staff account in database")
    void createDispatcherStaffAccount_success() {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "dispatch.anura@swiftdrop.lk",
            "SecurePass#2026",
            "Anura Silva",
            "+94777654321",
            "DISPATCHER"
        );

        UserSummary summary = adminUserService.createStaffUser(request);

        assertThat(summary.role()).isEqualTo("DISPATCHER");
        Optional<User> persisted = adminUserRepository.findById(summary.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getRole()).isEqualTo("DISPATCHER");
    }

    @Test
    @DisplayName("End-to-end: Duplicate email rejection in database")
    void createStaffUser_rejectsDuplicate() {
        CreateStaffUserRequest request1 = new CreateStaffUserRequest(
            "duplicate@swiftdrop.lk",
            "SecurePass#2026",
            "User One",
            "+94771111111",
            "COURIER"
        );
        adminUserService.createStaffUser(request1);

        CreateStaffUserRequest request2 = new CreateStaffUserRequest(
            "DUPLICATE@swiftdrop.lk",
            "AnotherPass#2026",
            "User Two",
            "+94772222222",
            "DISPATCHER"
        );

        assertThatThrownBy(() -> adminUserService.createStaffUser(request2))
            .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    @DisplayName("End-to-end: List users with pagination and role filter")
    void listUsers_withFilters() {
        adminUserService.createStaffUser(new CreateStaffUserRequest(
            "courier1@swiftdrop.lk", "SecurePass#2026", "Courier One", "+94771111111", "COURIER"
        ));
        adminUserService.createStaffUser(new CreateStaffUserRequest(
            "courier2@swiftdrop.lk", "SecurePass#2026", "Courier Two", "+94772222222", "COURIER"
        ));
        adminUserService.createStaffUser(new CreateStaffUserRequest(
            "dispatcher1@swiftdrop.lk", "SecurePass#2026", "Dispatcher One", "+94773333333", "DISPATCHER"
        ));

        UserListPage couriersOnly = adminUserService.listUsers("COURIER", null, 0, 10);
        assertThat(couriersOnly.totalElements()).isEqualTo(2);
        assertThat(couriersOnly.content()).allMatch(u -> "COURIER".equals(u.role()));

        UserListPage dispatchersOnly = adminUserService.listUsers("DISPATCHER", null, 0, 10);
        assertThat(dispatchersOnly.totalElements()).isEqualTo(1);
        assertThat(dispatchersOnly.content().get(0).role()).isEqualTo("DISPATCHER");
    }

    @Test
    @DisplayName("End-to-end: Enable and disable user account status in database")
    void updateUserStatus_enableAndDisable() {
        UserSummary created = adminUserService.createStaffUser(new CreateStaffUserRequest(
            "toggle@swiftdrop.lk", "SecurePass#2026", "Toggle User", "+94779999999", "COURIER"
        ));
        assertThat(created.enabled()).isTrue();

        UserSummary disabled = adminUserService.updateUserStatus(created.id(), false);
        assertThat(disabled.enabled()).isFalse();

        Optional<User> persisted = adminUserRepository.findById(created.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().isEnabled()).isFalse();

        UserSummary reEnabled = adminUserService.updateUserStatus(created.id(), true);
        assertThat(reEnabled.enabled()).isTrue();
    }
}
