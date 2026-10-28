package com.swiftdrop.auth.admin.dto;

import java.util.List;

public record UserListPage(
    List<UserSummary> content,
    long totalElements,
    int totalPages,
    int page,
    int size
) {}
