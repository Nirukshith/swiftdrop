package com.swiftdrop.operations.dispatch.client;

import java.util.List;

public record ParcelPageClientResponse(
    List<ParcelClientResponse> content,
    long totalElements,
    int totalPages,
    int page,
    int size
) {}
