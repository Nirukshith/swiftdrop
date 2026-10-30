package com.swiftdrop.operations.dispatch.client;

public record ParcelAssignmentClientRequest(
    String courierId,
    String assignedBy
) {}
