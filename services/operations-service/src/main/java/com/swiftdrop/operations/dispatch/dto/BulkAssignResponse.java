package com.swiftdrop.operations.dispatch.dto;

import java.util.List;

public record BulkAssignResponse(
    int totalRequested,
    int successfulCount,
    int failedCount,
    List<BulkAssignItemResult> results
) {}

