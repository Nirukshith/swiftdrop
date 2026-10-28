package com.swiftdrop.operations.dispatch.dto;

import java.util.List;

public record UnassignedParcelsPage(
    List<DispatchParcelSummary> content,
    long totalElements,
    int totalPages,
    int page,
    int size
) {}
