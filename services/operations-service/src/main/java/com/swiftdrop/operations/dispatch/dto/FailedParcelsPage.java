package com.swiftdrop.operations.dispatch.dto;

import java.util.List;

public record FailedParcelsPage(
    List<DispatchParcelSummary> content,
    long totalElements,
    int totalPages,
    int page,
    int size
) {}

