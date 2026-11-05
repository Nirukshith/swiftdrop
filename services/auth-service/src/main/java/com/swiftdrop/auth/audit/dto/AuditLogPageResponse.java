package com.swiftdrop.auth.audit.dto;

import java.util.List;

/**
 * Paginated audit log response DTO.
 * Aligned with OpenAPI contract and docs/audit-design.md (M8).
 */
public record AuditLogPageResponse(
    List<AuditRecordResponse> content,
    long totalElements,
    int totalPages,
    int page,
    int size
) {}
