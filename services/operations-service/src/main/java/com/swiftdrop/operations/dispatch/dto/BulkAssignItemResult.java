package com.swiftdrop.operations.dispatch.dto;

public record BulkAssignItemResult(
    String parcelId,
    String courierId,
    boolean success,
    String errorCode,
    String errorMessage
) {}

