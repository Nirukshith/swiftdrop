package com.swiftdrop.operations.dispatch.client;

public record ParcelAssignmentClientRequest(
    String courierId,
    String assignedBy,
    Boolean reassign
) {
    public ParcelAssignmentClientRequest(String courierId, String assignedBy) {
        this(courierId, assignedBy, false);
    }
}
