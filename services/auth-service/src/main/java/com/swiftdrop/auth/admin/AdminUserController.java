package com.swiftdrop.auth.admin;

import com.swiftdrop.auth.admin.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<UserListPage> listUsers(
        @RequestParam(required = false) String role,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        UserListPage response = adminUserService.listUsers(role, enabled, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<UserSummary> createStaffUser(
        @Valid @RequestBody CreateStaffUserRequest request
    ) {
        UserSummary summary = adminUserService.createStaffUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserSummary> updateUserStatus(
        @PathVariable String id,
        @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        UserSummary summary = adminUserService.updateUserStatus(id, request.enabled());
        return ResponseEntity.ok(summary);
    }
}
