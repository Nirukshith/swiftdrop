package com.swiftdrop.auth.audit;

import com.swiftdrop.auth.audit.dto.AuditLogPageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller for administrator audit log queries.
 * Restricted strictly to ROLE_ADMIN per ADR 001.
 * Owned by M8.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    public AdminAuditController(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public ResponseEntity<AuditLogPageResponse> getAuditLogs(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String actorId,
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        AuditLogPageResponse response = adminAuditService.getAuditLogs(
            action, actorId, targetType, fromDate, toDate, page, size
        );
        return ResponseEntity.ok(response);
    }
}
